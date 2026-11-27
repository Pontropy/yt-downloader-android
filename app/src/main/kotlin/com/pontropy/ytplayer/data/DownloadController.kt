package com.pontropy.ytplayer.data

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class QueueItemStatus {
    PENDING,
    DOWNLOADING,
    DONE,
    ERROR,
    CANCELLED,
}

data class QueueItem(
    val id: String,
    val url: String,
    val title: String? = null,
    val artist: String = "",
    val thumbnailUrl: String? = null,
    val durationSeconds: Int = 0,
    val quality: QualityPreset,
    val audioCode: String,
    val downloadSubtitles: Boolean = false,
    val formatSuffix: String? = null,
    val status: QueueItemStatus = QueueItemStatus.PENDING,
    val progressPercent: Float? = null,
    val stageLabel: String = "",
    val errorMessage: String? = null,
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: url
}

data class PendingDownloadSpec(
    val url: String,
    val preset: QualityPreset,
    val audioCode: String,
    val title: String? = null,
    val artist: String = "",
    val thumbnailUrl: String? = null,
    val durationSeconds: Int = 0,
    val downloadSubtitles: Boolean = false,
    val formatSuffix: String? = null,
)

data class PlaylistTrackEntry(
    val id: String,
    val url: String,
    val title: String,
    val artist: String = "",
    val durationSeconds: Int = 0,
    val thumbnailUrl: String? = null,
)

data class VideoPreview(
    val url: String,
    val title: String,
    val artist: String,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
    val maxHeight: Int?,
    val hasRussianAudio: Boolean,
    val hasOriginalAudio: Boolean,
    val isMusic: Boolean,
    val isPlaylist: Boolean = false,
    val availableAudioTracks: List<AudioTrackOption> = emptyList(),
    val availableVideoPresets: List<QualityPreset> = QualityPreset.videoEntries,
    val availableMusicPresets: List<QualityPreset> = QualityPreset.musicEntries,
    val videoFormatNotes: Map<QualityPreset, String> = emptyMap(),
    val musicFormatNotes: Map<QualityPreset, String> = emptyMap(),
    val formatsProbed: Boolean = false,
)

sealed interface PreviewState {
    data object Idle : PreviewState
    data class Loading(val url: String) : PreviewState
    data class Loaded(val preview: VideoPreview) : PreviewState
    data class Error(val url: String, val message: String) : PreviewState
}

sealed interface PlaylistLoadState {
    data object Idle : PlaylistLoadState
    data class Loading(val url: String) : PlaylistLoadState
    data class Loaded(
        val playlistTitle: String,
        val entries: List<PlaylistTrackEntry>,
    ) : PlaylistLoadState
    data class Error(val message: String) : PlaylistLoadState
}

data class DownloadProgressState(
    val isActive: Boolean = false,
    val activeCount: Int = 0,
    val activeItemTitle: String = "",
    val progressPercent: Float? = null,
    val etaSeconds: Long = -1L,
    val statusText: String = "Готов к загрузке",
    val logs: List<String> = emptyList(),
)

object MetadataJsonParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseSingleVideo(rawJson: String, url: String): VideoPreview? {
        val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull()
            ?: return null

        val title = (root.str("track") ?: root.str("title"))?.trim().orEmpty()
        if (title.isBlank()) return null

        val artist = (root.str("artist") ?: root.str("channel") ?: root.str("uploader"))
            ?.trim().orEmpty()
        val duration = root["duration"]?.jsonPrimitive?.floatOrNull?.toInt() ?: 0
        val thumb = root.str("thumbnail")

        var hasRu = false
        var hasOrig = false
        var maxH: Int? = null
        var hasAnyVideo = false
        var hasAnyAudio = false
        var bestAbr = 0f
        var aacAbr = 0f
        var opusAbr = 0f
        var hasFlac = false
        val tracksByCode = linkedMapOf<String, AudioTrackOption>()

        val formats = runCatching { root["formats"]?.jsonArray }.getOrNull()
        formats?.forEach { elem ->
            val fmt = runCatching { elem.jsonObject }.getOrNull() ?: return@forEach
            val acodec = fmt.str("acodec")
            val vcodec = fmt.str("vcodec")
            val ext = fmt.str("ext").orEmpty().lowercase()

            if (!acodec.isNullOrBlank() && acodec != "none") {
                hasAnyAudio = true
                val abrVal = fmt["abr"]?.jsonPrimitive?.floatOrNull
                    ?: fmt["tbr"]?.jsonPrimitive?.floatOrNull
                    ?: 0f
                if (abrVal > bestAbr) bestAbr = abrVal
                val aLow = acodec.lowercase()
                if (ext == "m4a" || "mp4a" in aLow || "aac" in aLow) {
                    if (abrVal > aacAbr) aacAbr = abrVal
                }
                if ("opus" in aLow || ext == "opus") {
                    if (abrVal > opusAbr) opusAbr = abrVal
                }
                if ("flac" in aLow || ext == "flac") {
                    hasFlac = true
                }

                val rawLang = fmt.str("language")
                val normLang = AudioLanguages.isValidCodeOrNull(rawLang)
                val note = fmt.str("format_note").orEmpty()
                val noteLow = note.lowercase()
                val isOrig = "original" in noteLow

                if (normLang != null && normLang.startsWith("ru")) hasRu = true
                if (isOrig) hasOrig = true

                if (normLang != null && normLang != AudioLanguages.ORIGINAL) {
                    val existing = tracksByCode[normLang]
                    if (existing == null || (isOrig && !existing.isOriginal)) {
                        tracksByCode[normLang] = AudioTrackOption(
                            code = normLang,
                            label = AudioLanguages.formatLabel(normLang, note),
                            isOriginal = isOrig,
                        )
                    }
                }
            }

            if (!vcodec.isNullOrBlank() && vcodec != "none") {
                val h = fmt["height"]?.jsonPrimitive?.intOrNull
                val prevMax = maxH
                if (h != null && h > 0) {
                    hasAnyVideo = true
                    if (prevMax == null || h > prevMax) maxH = h
                }
            }
        }

        val orderedTracks = buildList {
            if (hasOrig || tracksByCode.isNotEmpty()) {
                add(
                    AudioTrackOption(
                        code = AudioLanguages.ORIGINAL,
                        label = AudioLanguages.formatLabel(AudioLanguages.ORIGINAL),
                        isOriginal = true,
                    )
                )
            }
            tracksByCode.values
                .sortedWith(
                    compareBy<AudioTrackOption>(
                        { if (it.isOriginal) 0 else 1 },
                        { if (it.code == AudioLanguages.RUSSIAN) 0 else 1 },
                        { it.code },
                    )
                )
                .forEach { add(it) }
        }

        val webpageUrl = (root.str("webpage_url") ?: url).lowercase()
        val isMusic = "music.youtube.com" in webpageUrl ||
            (!root.str("track").isNullOrBlank() && !root.str("artist").isNullOrBlank())

        val formatsProbed = formats != null && formats.isNotEmpty()
        val availVideo = mutableListOf<QualityPreset>()
        val videoNotes = mutableMapOf<QualityPreset, String>()
        val availMusic = mutableListOf<QualityPreset>()
        val musicNotes = mutableMapOf<QualityPreset, String>()

        if (formatsProbed) {
            val h = maxH
            if (hasAnyVideo && h != null) {
                if (h >= 144) {
                    availVideo += QualityPreset.VIDEO_360P
                    videoNotes[QualityPreset.VIDEO_360P] = "до ${minOf(360, h)}p"
                }
                if (h >= 720) {
                    availVideo += QualityPreset.VIDEO_720P
                    videoNotes[QualityPreset.VIDEO_720P] = "HD"
                }
                if (h >= 1080) {
                    availVideo += QualityPreset.VIDEO_1080P
                    videoNotes[QualityPreset.VIDEO_1080P] = "FHD"
                }
                if (h >= 1440) {
                    availVideo += QualityPreset.VIDEO_1440P
                    videoNotes[QualityPreset.VIDEO_1440P] = "2K"
                }
                if (h >= 2160) {
                    availVideo += QualityPreset.VIDEO_2160P
                    videoNotes[QualityPreset.VIDEO_2160P] = "4K"
                }
                availVideo += QualityPreset.VIDEO_MAX
                videoNotes[QualityPreset.VIDEO_MAX] = "до ${h}p"
            }
            if (hasAnyAudio) {
                val srcKbps = if (bestAbr > 0f) "${bestAbr.toInt()}k" else "лучш."
                availMusic += QualityPreset.musicEntries
                musicNotes[QualityPreset.AUDIO_MP3] = "~V0 ($srcKbps)"
                musicNotes[QualityPreset.AUDIO_FLAC] = if (hasFlac) "натив" else "из $srcKbps"
                musicNotes[QualityPreset.AUDIO_M4A] = if (aacAbr > 0f) "AAC ${aacAbr.toInt()}k" else "AAC"
                musicNotes[QualityPreset.AUDIO_OPUS] = if (opusAbr > 0f) "${opusAbr.toInt()}k" else "конверт."
            }
        } else {
            if (!isMusic) availVideo += QualityPreset.videoEntries
            availMusic += QualityPreset.musicEntries
        }

        return VideoPreview(
            url = url,
            title = title,
            artist = artist,
            durationSeconds = duration,
            thumbnailUrl = thumb,
            maxHeight = maxH,
            hasRussianAudio = hasRu,
            hasOriginalAudio = hasOrig,
            isMusic = isMusic,
            isPlaylist = UrlValidator.isPlaylistUrl(url),
            availableAudioTracks = orderedTracks,
            availableVideoPresets = availVideo,
            availableMusicPresets = availMusic,
            videoFormatNotes = videoNotes,
            musicFormatNotes = musicNotes,
            formatsProbed = formatsProbed,
        )
    }

    fun parsePlaylist(rawJson: String): Pair<String, List<PlaylistTrackEntry>>? {
        val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull()
            ?: return null
        val plTitle = root.str("title")?.trim()?.ifBlank { "Плейлист" } ?: "Плейлист"
        val entriesJson = runCatching { root["entries"]?.jsonArray }.getOrNull() ?: return null

        val list = entriesJson.mapIndexedNotNull { idx, elem ->
            val obj = runCatching { elem.jsonObject }.getOrNull() ?: return@mapIndexedNotNull null
            val rawTitle = obj.str("title")?.trim().orEmpty()
            if (rawTitle == "[Private video]" || rawTitle == "[Deleted video]" || rawTitle == "[Unavailable video]") {
                return@mapIndexedNotNull null
            }
            val id = obj.str("id") ?: "item_$idx"
            val rawEntryUrl = obj.str("webpage_url") ?: obj.str("url")
            val entryUrl = when {
                rawEntryUrl != null && rawEntryUrl.startsWith("http", ignoreCase = true) -> rawEntryUrl
                id.isNotBlank() -> "https://www.youtube.com/watch?v=$id"
                else -> null
            } ?: return@mapIndexedNotNull null

            val title = rawTitle.ifBlank { entryUrl }
            val artist = (obj.str("uploader") ?: obj.str("channel") ?: obj.str("artist")).orEmpty()
            val duration = obj["duration"]?.jsonPrimitive?.floatOrNull?.toInt() ?: 0
            val thumb = obj.str("thumbnail") ?: runCatching {
                obj["thumbnails"]?.jsonArray?.lastOrNull()?.jsonObject?.str("url")
            }.getOrNull()

            PlaylistTrackEntry(
                id = id,
                url = entryUrl,
                title = title,
                artist = artist,
                durationSeconds = duration,
                thumbnailUrl = thumb,
            )
        }
        return plTitle to list
    }

    private fun JsonObject.str(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
}

class DownloadController(
    private val context: Context,
    private val settingsRepository: AppSettingsRepository,
    private val libraryRepository: MediaLibraryRepository,
    private val appScope: CoroutineScope,
) {
    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _progress = MutableStateFlow(DownloadProgressState())
    val progress: StateFlow<DownloadProgressState> = _progress.asStateFlow()

    private val _preview = MutableStateFlow<PreviewState>(PreviewState.Idle)
    val preview: StateFlow<PreviewState> = _preview.asStateFlow()

    private val _playlistState = MutableStateFlow<PlaylistLoadState>(PlaylistLoadState.Idle)
    val playlistState: StateFlow<PlaylistLoadState> = _playlistState.asStateFlow()

    private var previewJob: Job? = null
    private var playlistJob: Job? = null
    private var dispatcherJob: Job? = null
    private val activeProcessIds = ConcurrentHashMap.newKeySet<String>()
    private val cancelledIds = ConcurrentHashMap.newKeySet<String>()
    private var batchItemIds: Set<String> = emptySet()
    private var batchFloorPercent: Float = 0f

    fun clearPreview() {
        previewJob?.cancel()
        _preview.value = PreviewState.Idle
    }

    fun clearPlaylistState() {
        playlistJob?.cancel()
        _playlistState.value = PlaylistLoadState.Idle
    }

    fun fetchPreview(url: String, ensureReady: suspend () -> Boolean) {
        val cleaned = url.trim()
        if (!UrlValidator.isValidHttpUrl(cleaned)) {
            clearPreview()
            return
        }
        val current = _preview.value
        if (current is PreviewState.Loaded && current.preview.url == cleaned) return

        previewJob?.cancel()
        previewJob = appScope.launch {
            _preview.value = PreviewState.Loading(cleaned)
            if (!ensureReady()) {
                _preview.value = PreviewState.Error(cleaned, "Движок yt-dlp ещё не готов")
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (UrlValidator.isPurePlaylistUrl(cleaned)) {
                        val req = YoutubeDLRequest(cleaned).apply {
                            addOption("--flat-playlist")
                            addOption("--dump-single-json")
                            addOption("--skip-download")
                            addOption("--no-warnings")
                        }
                        val resp = YoutubeDL.getInstance().execute(req, "preview_pl_${UUID.randomUUID()}")
                        val parsedPl = MetadataJsonParser.parsePlaylist(resp.out)
                        if (parsedPl != null) {
                            val (plTitle, entries) = parsedPl
                            if (_playlistState.value is PlaylistLoadState.Idle) {
                                _playlistState.value = PlaylistLoadState.Loaded(plTitle, entries)
                            }
                            return@runCatching VideoPreview(
                                url = cleaned,
                                title = "$plTitle (${entries.size})",
                                artist = "Плейлист / альбом",
                                durationSeconds = 0,
                                thumbnailUrl = entries.firstOrNull()?.thumbnailUrl,
                                maxHeight = null,
                                hasRussianAudio = false,
                                hasOriginalAudio = false,
                                isMusic = cleaned.contains("music.youtube.com", ignoreCase = true),
                                isPlaylist = true,
                            )
                        }
                    }

                    val req = YoutubeDLRequest(cleaned).apply {
                        addOption("--dump-single-json")
                        addOption("--no-playlist")
                        addOption("--skip-download")
                        addOption("--no-warnings")
                        applyCookiesIfPresent(this)
                    }
                    val resp = YoutubeDL.getInstance().execute(req, "preview_${UUID.randomUUID()}")
                    val parsed = MetadataJsonParser.parseSingleVideo(resp.out, cleaned)
                    if (parsed != null) return@runCatching parsed

                    // Fallback to mapper getInfo if JSON output was empty
                    val infoReq = YoutubeDLRequest(cleaned).apply {
                        addOption("--no-playlist")
                        addOption("--no-warnings")
                        applyCookiesIfPresent(this)
                    }
                    val info = YoutubeDL.getInstance().getInfo(infoReq)
                    val formats = info.formats.orEmpty()
                    val hasRu = formats.any { fmt ->
                        val acodec = fmt.acodec
                        acodec != null && acodec != "none" &&
                            (fmt.formatNote?.contains("Russian", ignoreCase = true) == true ||
                                fmt.formatId?.contains("ru", ignoreCase = true) == true)
                    }
                    val hasOrig = formats.any { fmt ->
                        fmt.formatNote?.contains("original", ignoreCase = true) == true
                    }
                    val maxH = formats.mapNotNull { fmt ->
                        if (fmt.vcodec != null && fmt.vcodec != "none" && fmt.height > 0) fmt.height else null
                    }.maxOrNull()
                    VideoPreview(
                        url = cleaned,
                        title = info.title?.takeIf { it.isNotBlank() } ?: cleaned,
                        artist = info.uploader?.takeIf { it.isNotBlank() } ?: "",
                        durationSeconds = info.duration,
                        thumbnailUrl = info.thumbnail,
                        maxHeight = maxH,
                        hasRussianAudio = hasRu,
                        hasOriginalAudio = hasOrig,
                        isMusic = cleaned.contains("music.youtube.com", ignoreCase = true),
                        isPlaylist = UrlValidator.isPlaylistUrl(cleaned),
                    )
                }
            }

            result.onSuccess { meta ->
                _preview.value = PreviewState.Loaded(meta)
                _queue.update { list ->
                    list.map { item ->
                        if (item.url == cleaned && item.title == null) {
                            item.copy(
                                title = meta.title,
                                artist = meta.artist,
                                thumbnailUrl = meta.thumbnailUrl,
                                durationSeconds = meta.durationSeconds,
                            )
                        } else {
                            item
                        }
                    }
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                _preview.value = PreviewState.Error(
                    cleaned,
                    mapFriendlyError(err.message ?: "Не удалось получить превью"),
                )
            }
        }
    }

    fun loadPlaylistEntries(url: String, ensureReady: suspend () -> Boolean) {
        val cleaned = url.trim()
        playlistJob?.cancel()
        playlistJob = appScope.launch {
            _playlistState.value = PlaylistLoadState.Loading(cleaned)
            if (!ensureReady()) {
                _playlistState.value = PlaylistLoadState.Error("Движок yt-dlp ещё не готов")
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val req = YoutubeDLRequest(cleaned).apply {
                        addOption("--flat-playlist")
                        addOption("--dump-single-json")
                        addOption("--skip-download")
                        addOption("--no-warnings")
                        applyCookiesIfPresent(this)
                    }
                    val resp = YoutubeDL.getInstance().execute(req, "pl_${UUID.randomUUID()}")
                    MetadataJsonParser.parsePlaylist(resp.out)
                        ?: throw IllegalStateException("Плейлист пуст или не распознан")
                }
            }
            result.onSuccess { (title, entries) ->
                _playlistState.value = PlaylistLoadState.Loaded(title, entries)
            }.onFailure { err ->
                if (err is CancellationException) throw err
                _playlistState.value = PlaylistLoadState.Error(
                    mapFriendlyError(err.message ?: "Не удалось загрузить список плейлиста"),
                )
            }
        }
    }

    fun enqueueSpecs(
        specs: List<PendingDownloadSpec>,
        ensureReady: suspend () -> Boolean,
    ) {
        if (specs.isEmpty()) return
        val loadedMeta = (_preview.value as? PreviewState.Loaded)?.preview

        val newItems = specs.map { spec ->
            val matchedMeta = loadedMeta?.takeIf { it.url == spec.url }
            QueueItem(
                id = UUID.randomUUID().toString(),
                url = spec.url,
                title = spec.title ?: matchedMeta?.title,
                artist = spec.artist.ifBlank { matchedMeta?.artist.orEmpty() },
                thumbnailUrl = spec.thumbnailUrl ?: matchedMeta?.thumbnailUrl,
                durationSeconds = if (spec.durationSeconds > 0) spec.durationSeconds else (matchedMeta?.durationSeconds ?: 0),
                quality = spec.preset,
                audioCode = spec.audioCode,
                downloadSubtitles = spec.downloadSubtitles,
                formatSuffix = spec.formatSuffix,
            )
        }
        val wasRunning = _progress.value.isActive
        _queue.update { it + newItems }
        batchItemIds = if (wasRunning) {
            batchItemIds + newItems.map { it.id }
        } else {
            batchFloorPercent = 0f
            newItems.map { it.id }.toSet()
        }
        val fmtSummary = newItems.map { it.quality.shortLabel }.distinct().take(4).joinToString(", ")
        appendLog("В очередь: ${newItems.size} задач ($fmtSummary)")

        DownloadForegroundService.ensureStarted(context)

        if (dispatcherJob?.isActive != true) {
            dispatcherJob = appScope.launch {
                runQueuePool(ensureReady)
            }
        }
    }

    fun removeItem(itemId: String) {
        _queue.update { list ->
            list.filterNot { it.id == itemId && it.status != QueueItemStatus.DOWNLOADING }
        }
    }

    fun clearFinished() {
        _queue.update { list ->
            val hasFinished = list.any {
                it.status in setOf(QueueItemStatus.DONE, QueueItemStatus.ERROR, QueueItemStatus.CANCELLED)
            }
            val updated = if (hasFinished) {
                list.filterNot {
                    it.status in setOf(QueueItemStatus.DONE, QueueItemStatus.ERROR, QueueItemStatus.CANCELLED)
                }
            } else {
                list.filter { it.status == QueueItemStatus.DOWNLOADING }
            }
            batchItemIds = updated.map { it.id }.toSet()
            if (batchItemIds.isEmpty()) batchFloorPercent = 0f
            updated
        }
    }

    fun cancelAllActive() {
        _queue.update { list ->
            list.map {
                if (it.status == QueueItemStatus.PENDING) {
                    it.copy(status = QueueItemStatus.CANCELLED)
                } else {
                    it
                }
            }
        }
        val activeSnapshot = activeProcessIds.toList()
        if (activeSnapshot.isEmpty()) return
        cancelledIds.addAll(activeSnapshot)
        appScope.launch(Dispatchers.IO) {
            activeSnapshot.forEach { pid ->
                runCatching { YoutubeDL.getInstance().destroyProcessById(pid) }
            }
        }
        _progress.update { it.copy(statusText = "Отмена загрузок...") }
    }

    suspend fun updateYtDlp(): Result<String> = withContext(Dispatchers.IO) {
        val libResult = runCatching {
            val status = YoutubeDL.getInstance().updateYoutubeDL(
                context,
                YoutubeDL.UpdateChannel.STABLE,
            )
            when (status) {
                YoutubeDL.UpdateStatus.DONE -> "yt-dlp успешно обновлён до последней версии"
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Установлена актуальная версия yt-dlp"
                null -> "Обновление завершено"
            }
        }
        if (libResult.isSuccess) return@withContext libResult

        // Fallback direct download bypassing api.github.com JSON API rate limits
        runCatching {
            val conn = (java.net.URL("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp")
                .openConnection() as java.net.HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "YT-Downloader-Android")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                error("HTTP $code")
            }
            val ytdlpDir = File(File(context.noBackupFilesDir, "youtubedl-android"), "yt-dlp")
            ytdlpDir.mkdirs()
            val tmp = File(ytdlpDir, "yt-dlp.dl")
            conn.inputStream.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out) }
            }
            val header = tmp.inputStream().use { it.readNBytes(32) }.toString(Charsets.ISO_8859_1)
            if (tmp.length() < 500_000L || !header.startsWith("#!")) {
                tmp.delete()
                error("Получен повреждённый файл yt-dlp")
            }
            val target = File(ytdlpDir, "yt-dlp")
            if (target.exists()) target.delete()
            tmp.renameTo(target)
            "yt-dlp успешно обновлён напрямую с GitHub"
        }
    }

    private suspend fun runQueuePool(ensureReady: suspend () -> Boolean) {
        if (!ensureReady()) {
            appendLog("Ошибка: yt-dlp/ffmpeg не инициализированы")
            _progress.update {
                it.copy(isActive = false, activeCount = 0, statusText = "Ошибка инициализации движка")
            }
            return
        }

        val maxWorkers = settingsRepository.settingsFlow.first().maxConcurrentDownloads.coerceIn(1, 3)

        while (_queue.value.any { it.status == QueueItemStatus.PENDING }) {
            val workerJobs = (0 until maxWorkers).map {
                appScope.launch {
                    while (true) {
                        val claimed = claimNextPendingItem() ?: break
                        processSingleItem(claimed)
                    }
                }
            }
            workerJobs.joinAll()
        }

        _progress.update {
            it.copy(
                isActive = false,
                activeCount = 0,
                progressPercent = null,
                etaSeconds = -1L,
            )
        }
    }

    private fun claimNextPendingItem(): QueueItem? {
        var claimed: QueueItem? = null
        _queue.update { list ->
            val idx = list.indexOfFirst { it.status == QueueItemStatus.PENDING }
            if (idx == -1) {
                claimed = null
                list
            } else {
                val updatedItem = list[idx].copy(
                    status = QueueItemStatus.DOWNLOADING,
                    stageLabel = "Подготовка...",
                )
                claimed = updatedItem
                list.toMutableList().apply { set(idx, updatedItem) }
            }
        }
        return claimed
    }

    private suspend fun processSingleItem(next: QueueItem) {
        activeProcessIds.add(next.id)
        refreshAggregateProgress("Подготовка: ${next.displayTitle}", -1L)
        appendLog(
            "Старт [${next.quality.shortLabel} / ${AudioLanguages.shortLabel(next.audioCode)}]: ${next.displayTitle}"
        )

        val stagingDir = File(context.cacheDir, "dl_${next.id}").apply {
            deleteRecursively()
            mkdirs()
        }

        val subPid = "${next.id}_subs"
        val subStagingDir = File(stagingDir, "subs").apply { mkdirs() }

        try {
            var itemTitle = next.title.orEmpty()
            var itemArtist = next.artist
            var itemThumb = next.thumbnailUrl
            var itemDuration = next.durationSeconds

            val request = YoutubeDLRequest(next.url).apply {
                PresetFlagBuilder.buildArgs(
                    next.quality,
                    next.audioCode,
                    downloadSubtitles = false,
                ).forEach { (flag, value) ->
                    if (value != null) addOption(flag, value) else addOption(flag)
                }
                addOption("--newline")
                addOption("--no-playlist")
                addOption("--no-mtime")
                applyCookiesIfPresent(this)
                if (itemTitle.isBlank()) {
                    addOption("--write-info-json")
                }
                addOption("-P", stagingDir.absolutePath)
                addOption("-o", "%(title)s [%(id)s].%(ext)s")
            }

            withContext(Dispatchers.IO) {
                coroutineScope {
                    val subJob = if (next.downloadSubtitles && !next.quality.isAudioOnly) {
                        activeProcessIds.add(subPid)
                        async(Dispatchers.IO) {
                            runCatching {
                                val normAudio = AudioLanguages.normalizeCode(next.audioCode)
                                val subLangs = if (normAudio != AudioLanguages.ORIGINAL && normAudio != AudioLanguages.RUSSIAN) {
                                    "$normAudio,ru,en"
                                } else {
                                    "ru,en"
                                }
                                val subReq = YoutubeDLRequest(next.url).apply {
                                    addOption("--skip-download")
                                    addOption("--ignore-errors")
                                    addOption("--retries", "1")
                                    addOption("--extractor-retries", "1")
                                    addOption("--no-playlist")
                                    addOption("--no-warnings")
                                    addOption("--write-subs")
                                    addOption("--write-auto-subs")
                                    addOption("--sub-langs", subLangs)
                                    addOption("--convert-subs", "srt")
                                    applyCookiesIfPresent(this)
                                    addOption("-P", subStagingDir.absolutePath)
                                    addOption("-o", "%(title)s [%(id)s].%(ext)s")
                                }
                                YoutubeDL.getInstance().execute(subReq, subPid)
                            }.onFailure {
                                if (next.id !in cancelledIds && subPid !in cancelledIds) {
                                    appendLog("[${next.quality.shortLabel}] Субтитры недоступны — продолжаю без .srt")
                                }
                            }
                        }
                    } else {
                        null
                    }

                    var streamIndex = 0
                    var highestItemPercent = 0f
                    YoutubeDL.getInstance().execute(
                        request,
                        next.id,
                    ) { progress: Float, etaInSeconds: Long, line: String ->
                        if (line.contains("[download] Destination:", ignoreCase = true)) {
                            streamIndex += 1
                        }
                        val cleanedPercent = progress.takeIf { it >= 0f }
                        val mappedPct: Float? = when {
                            line.contains("[Merger]", ignoreCase = true) ||
                                line.contains("[ExtractAudio]", ignoreCase = true) ||
                                line.contains("Metadata", ignoreCase = true) -> 99f
                            cleanedPercent == null -> null
                            next.quality.isAudioOnly -> (cleanedPercent * 0.96f).coerceIn(0f, 96f)
                            streamIndex <= 1 -> (cleanedPercent * 0.85f).coerceIn(0f, 85f)
                            else -> (85f + cleanedPercent * 0.13f).coerceIn(85f, 98f)
                        }
                        val monotonicPct = mappedPct?.let {
                            highestItemPercent = maxOf(highestItemPercent, it)
                            highestItemPercent
                        }
                        val stageLabel = when {
                            line.contains("[Merger]", ignoreCase = true) -> "Склейка MP4..."
                            line.contains("[ExtractAudio]", ignoreCase = true) -> "Конвертация аудио..."
                            line.contains("Metadata", ignoreCase = true) -> "Запись тегов..."
                            line.contains("Thumbnail", ignoreCase = true) -> "Встраивание обложки..."
                            monotonicPct != null -> "${"%.0f".format(monotonicPct)}%"
                            else -> "Обработка..."
                        }
                        _queue.update { list ->
                            list.map {
                                if (it.id == next.id) {
                                    it.copy(
                                        title = itemTitle.ifBlank { it.title },
                                        progressPercent = monotonicPct ?: it.progressPercent,
                                        stageLabel = stageLabel,
                                    )
                                } else {
                                    it
                                }
                            }
                        }
                        refreshAggregateProgress(
                            fallbackStatus = "${next.quality.shortLabel}: $stageLabel",
                            etaSeconds = etaInSeconds,
                        )
                        if (line.isNotBlank() && !line.startsWith("[download]  ")) {
                            appendLog("[${next.quality.shortLabel}] ${line.trim()}")
                        }
                    }

                    if (subJob != null) {
                        val completedSubs = withTimeoutOrNull(5_000L) {
                            subJob.await()
                            true
                        }
                        if (completedSubs == null) {
                            runCatching { YoutubeDL.getInstance().destroyProcessById(subPid) }
                            subJob.cancel()
                        }
                    }
                }
            }

            if (next.id in cancelledIds || subPid in cancelledIds) {
                updateItemStatus(next.id, QueueItemStatus.CANCELLED, stage = "Отменено")
                refreshAggregateProgress("Загрузка отменена", -1L)
                appendLog("Отменено: ${next.displayTitle}")
                return
            }

            if (itemTitle.isBlank()) {
                stagingDir.listFiles()
                    ?.firstOrNull { it.isFile && it.name.endsWith(".info.json", ignoreCase = true) }
                    ?.let { infoFile ->
                        runCatching {
                            MetadataJsonParser.parseSingleVideo(infoFile.readText(), next.url)
                        }.getOrNull()?.let { parsed ->
                            itemTitle = parsed.title
                            if (itemArtist.isBlank()) itemArtist = parsed.artist
                            if (itemThumb.isNullOrBlank()) itemThumb = parsed.thumbnailUrl
                            if (itemDuration <= 0) itemDuration = parsed.durationSeconds
                        }
                    }
            }

            val outputFile = findFinalOutputFile(stagingDir, next.quality)
                ?: throw IllegalStateException("Итоговый файл не найден после завершения yt-dlp")
            val subFile = if (!next.quality.isAudioOnly) findSubtitleFile(subStagingDir) else null

            val currentSettings = settingsRepository.settingsFlow.first()
            val customTreeUri = if (next.quality.isAudioOnly) {
                currentSettings.musicTreeUri
            } else {
                currentSettings.videoTreeUri
            }

            val entry = libraryRepository.importDownloadedFile(
                stagedFile = outputFile,
                subtitleFile = subFile,
                title = itemTitle.ifBlank { outputFile.nameWithoutExtension },
                artist = itemArtist,
                sourceUrl = next.url,
                thumbnailUrl = itemThumb,
                durationSeconds = itemDuration,
                preset = next.quality,
                audioCode = next.audioCode,
                exportPublic = currentSettings.exportToPublicDownloads,
                customTreeUri = customTreeUri,
                formatSuffix = next.formatSuffix,
            )

            _queue.update { list ->
                list.map {
                    if (it.id == next.id) {
                        it.copy(
                            title = entry.title,
                            artist = entry.artist,
                            thumbnailUrl = entry.thumbnailUrl,
                            durationSeconds = entry.durationSeconds,
                            status = QueueItemStatus.DONE,
                            progressPercent = 100f,
                            stageLabel = "Готово",
                        )
                    } else {
                        it
                    }
                }
            }
            refreshAggregateProgress("Готово: ${entry.fileName}", 0L, completedPercent = 100f)
            appendLog("Сохранено [${next.quality.shortLabel}]: ${entry.fileName}")
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val wasCancelled = next.id in cancelledIds || subPid in cancelledIds
            if (wasCancelled) {
                updateItemStatus(next.id, QueueItemStatus.CANCELLED, stage = "Отменено")
                refreshAggregateProgress("Загрузка отменена", -1L)
                appendLog("Отменено: ${next.displayTitle}")
            } else {
                val nonBlankLines = t.message.orEmpty()
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toList()
                val rawErr = (
                    nonBlankLines.lastOrNull { it.contains("ERROR", ignoreCase = true) }
                        ?: nonBlankLines.lastOrNull()
                        ?: t.javaClass.simpleName
                    ).take(200)
                val errText = mapFriendlyError(rawErr)
                updateItemStatus(next.id, QueueItemStatus.ERROR, error = errText, stage = "Ошибка")
                refreshAggregateProgress("Ошибка: $errText", -1L)
                appendLog("Ошибка [${next.quality.shortLabel}]: $errText")
            }
        } finally {
            cancelledIds.remove(next.id)
            cancelledIds.remove(subPid)
            activeProcessIds.remove(next.id)
            activeProcessIds.remove(subPid)
            stagingDir.deleteRecursively()
        }
    }

    private fun applyCookiesIfPresent(request: YoutubeDLRequest) {
        val cookieFile = File(context.filesDir, "cookies.txt")
        if (cookieFile.isFile && cookieFile.length() > 0L) {
            request.addOption("--cookies", cookieFile.absolutePath)
        }
    }

    private fun mapFriendlyError(raw: String): String {
        val low = raw.lowercase()
        return when {
            "not a bot" in low || "sign in to confirm" in low || "login_required" in low ->
                "$raw | Добавьте cookies.txt в Настройках или смените VPN"
            else -> raw
        }
    }

    private fun refreshAggregateProgress(
        fallbackStatus: String,
        etaSeconds: Long,
        completedPercent: Float? = null,
    ) {
        val currentQueue = _queue.value
        val active = currentQueue.filter { it.status == QueueItemStatus.DOWNLOADING }
        val pendingCount = currentQueue.count { it.status == QueueItemStatus.PENDING }
        if (active.isEmpty()) {
            val doneInBatch = currentQueue.count { it.id in batchItemIds && it.status == QueueItemStatus.DONE }
            val finishedPct = when {
                pendingCount > 0 -> batchFloorPercent
                doneInBatch > 0 && completedPercent == 100f -> 100f
                else -> completedPercent ?: batchFloorPercent
            }
            if (pendingCount == 0) {
                batchFloorPercent = 0f
            }
            _progress.update {
                it.copy(
                    isActive = pendingCount > 0,
                    activeCount = 0,
                    progressPercent = finishedPct,
                    etaSeconds = etaSeconds,
                    statusText = fallbackStatus,
                )
            }
            return
        }

        val batch = currentQueue.filter { it.id in batchItemIds }.ifEmpty { active }
        val doneCount = batch.count { it.status == QueueItemStatus.DONE }
        val abandonedCount = batch.count {
            it.status == QueueItemStatus.ERROR || it.status == QueueItemStatus.CANCELLED
        }
        val effectiveTotal = (batch.size - abandonedCount).coerceAtLeast(active.size)
        val activeKnown = active.mapNotNull { it.progressPercent }
        val rawBatchPct = if (effectiveTotal > 0 && (doneCount > 0 || activeKnown.isNotEmpty())) {
            val activeSum = active.sumOf { (it.progressPercent ?: 0f).toDouble() }.toFloat()
            ((doneCount * 100f + activeSum) / effectiveTotal).coerceIn(0f, 99.9f)
        } else {
            null
        }
        val smoothPct = rawBatchPct?.let {
            batchFloorPercent = maxOf(batchFloorPercent, it)
            batchFloorPercent
        }
        val summaryTitle = if (active.size == 1) {
            active.first().displayTitle
        } else {
            "Загружается потоков: ${active.size}"
        }
        val status = if (active.size == 1) {
            val only = active.first()
            "${only.quality.shortLabel}: ${only.stageLabel.ifBlank { fallbackStatus }}"
        } else {
            val pctStr = smoothPct?.let { "${"%.0f".format(it)}%" } ?: "..."
            "Параллельно: ${active.size} • в очереди: $pendingCount • $pctStr"
        }

        _progress.update {
            it.copy(
                isActive = true,
                activeCount = active.size,
                activeItemTitle = summaryTitle,
                progressPercent = smoothPct,
                etaSeconds = if (active.size == 1) etaSeconds else -1L,
                statusText = status,
            )
        }
    }

    private fun findFinalOutputFile(dir: File, preset: QualityPreset): File? {
        val preferredExts = when (preset) {
            QualityPreset.AUDIO_MP3 -> listOf("mp3", "m4a", "opus")
            QualityPreset.AUDIO_FLAC -> listOf("flac", "m4a", "opus")
            QualityPreset.AUDIO_M4A -> listOf("m4a", "aac", "mp3")
            QualityPreset.AUDIO_OPUS -> listOf("opus", "ogg", "m4a")
            else -> listOf("mp4", "mkv", "webm")
        }
        val files = dir.listFiles()?.filter {
            it.isFile &&
                !it.name.endsWith(".part") &&
                it.extension.lowercase() !in setOf("srt", "vtt", "ass", "lrc", "jpg", "jpeg", "png", "webp", "json", "description")
        }.orEmpty()
        for (ext in preferredExts) {
            files.firstOrNull { it.extension.equals(ext, ignoreCase = true) }?.let { return it }
        }
        return files.maxByOrNull { it.length() }
    }

    private fun findSubtitleFile(dir: File): File? {
        val srts = dir.listFiles()?.filter {
            it.isFile && it.extension.equals("srt", ignoreCase = true)
        }.orEmpty()
        return srts.firstOrNull { it.name.contains(".ru.", ignoreCase = true) } ?: srts.firstOrNull()
    }

    private fun updateItemStatus(
        itemId: String,
        status: QueueItemStatus,
        error: String? = null,
        stage: String = "",
    ) {
        _queue.update { list ->
            list.map {
                if (it.id == itemId) {
                    it.copy(status = status, errorMessage = error, stageLabel = stage)
                } else {
                    it
                }
            }
        }
    }

    private fun appendLog(line: String) {
        _progress.update {
            it.copy(logs = (it.logs + line).takeLast(80))
        }
    }
}
