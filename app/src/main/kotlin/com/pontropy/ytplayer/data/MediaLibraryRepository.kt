package com.pontropy.ytplayer.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class MediaEntry(
    val id: String,
    val title: String,
    val artist: String = "",
    val sourceUrl: String,
    val filePath: String,
    val fileName: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int = 0,
    val qualityLabel: String,
    val audioLabel: String,
    val isAudioOnly: Boolean,
    val fileSizeBytes: Long,
    val createdAtMillis: Long,
    val subtitlePath: String? = null,
    val isFavorite: Boolean = false,
    val lastPositionMs: Long = 0L,
    val presetId: String = "",
) {
    val extension: String
        get() = File(fileName).extension.uppercase()
}

@Serializable
data class UserPlaylist(
    val id: String,
    val name: String,
    val entryIds: List<String> = emptyList(),
    val createdAtMillis: Long,
)

class MediaLibraryRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val mutex = Mutex()

    val mediaDir: File = File(context.filesDir, "media").apply { mkdirs() }
    private val subsDir: File = File(mediaDir, "subs").apply { mkdirs() }
    private val indexFile: File = File(context.filesDir, "library.json")
    private val playlistsFile: File = File(context.filesDir, "playlists.json")

    private val _entries = MutableStateFlow<List<MediaEntry>>(emptyList())
    val entries: StateFlow<List<MediaEntry>> = _entries.asStateFlow()

    private val _playlists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()

    init {
        _entries.value = loadFromDisk()
        _playlists.value = loadPlaylistsFromDisk()
    }

    private fun loadFromDisk(): List<MediaEntry> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val decoded = json.decodeFromString<List<MediaEntry>>(indexFile.readText())
            decoded.filter { File(it.filePath).exists() }
                .sortedByDescending { it.createdAtMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadPlaylistsFromDisk(): List<UserPlaylist> {
        if (!playlistsFile.exists()) return emptyList()
        return try {
            json.decodeFromString<List<UserPlaylist>>(playlistsFile.readText())
                .sortedByDescending { it.createdAtMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveToDisk(list: List<MediaEntry>) {
        indexFile.writeText(json.encodeToString(list))
    }

    private fun savePlaylistsToDisk(list: List<UserPlaylist>) {
        playlistsFile.writeText(json.encodeToString(list))
    }

    suspend fun importDownloadedFile(
        stagedFile: File,
        subtitleFile: File? = null,
        title: String,
        artist: String,
        sourceUrl: String,
        thumbnailUrl: String?,
        durationSeconds: Int,
        preset: QualityPreset,
        audioCode: String,
        exportPublic: Boolean,
        customTreeUri: String? = null,
        formatSuffix: String? = null,
    ): MediaEntry = withContext(Dispatchers.IO) {
        mutex.withLock {
            mediaDir.mkdirs()
            subsDir.mkdirs()
            val id = UUID.randomUUID().toString()
            val cleanTitleBase = stagedFile.nameWithoutExtension
                .replace(Regex("""[\\/:*?"<>|]+"""), "_")
                .take(75)
                .ifBlank { "media_${System.currentTimeMillis()}" }
            val safeBase = if (!formatSuffix.isNullOrBlank()) {
                "${cleanTitleBase} [$formatSuffix]"
            } else {
                cleanTitleBase
            }
            val ext = stagedFile.extension.lowercase().ifBlank {
                when (preset) {
                    QualityPreset.AUDIO_MP3 -> "mp3"
                    QualityPreset.AUDIO_FLAC -> "flac"
                    QualityPreset.AUDIO_M4A -> "m4a"
                    QualityPreset.AUDIO_OPUS -> "opus"
                    else -> "mp4"
                }
            }

            var targetFile = File(mediaDir, "$safeBase.$ext")
            var counter = 1
            while (targetFile.exists()) {
                targetFile = File(mediaDir, "${safeBase}_$counter.$ext")
                counter++
            }

            stagedFile.copyTo(targetFile, overwrite = true)
            stagedFile.delete()

            var importedSubPath: String? = null
            if (subtitleFile != null && subtitleFile.exists()) {
                val targetSub = File(subsDir, "$id.srt")
                runCatching {
                    subtitleFile.copyTo(targetSub, overwrite = true)
                    subtitleFile.delete()
                    importedSubPath = targetSub.absolutePath
                }
            }

            val exportedCustom = if (!customTreeUri.isNullOrBlank()) {
                exportToSafFolder(targetFile, ext, customTreeUri)
            } else {
                false
            }
            if (!exportedCustom && exportPublic) {
                exportToMediaStore(targetFile, ext, preset.isAudioOnly)
            }

            val entry = MediaEntry(
                id = id,
                title = title.ifBlank { cleanTitleBase },
                artist = artist,
                sourceUrl = sourceUrl,
                filePath = targetFile.absolutePath,
                fileName = targetFile.name,
                thumbnailUrl = thumbnailUrl,
                durationSeconds = durationSeconds,
                qualityLabel = preset.shortLabel,
                audioLabel = AudioLanguages.shortLabel(audioCode),
                isAudioOnly = preset.isAudioOnly || ext in setOf("mp3", "flac", "m4a", "opus"),
                fileSizeBytes = targetFile.length(),
                createdAtMillis = System.currentTimeMillis(),
                subtitlePath = importedSubPath,
                presetId = preset.id,
            )

            val updated = listOf(entry) + _entries.value.filter { it.filePath != entry.filePath }
            _entries.value = updated
            saveToDisk(updated)
            entry
        }
    }

    suspend fun toggleFavorite(entryId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _entries.value.map {
                if (it.id == entryId) it.copy(isFavorite = !it.isFavorite) else it
            }
            _entries.value = updated
            saveToDisk(updated)
        }
    }

    suspend fun updatePlaybackPosition(entryId: String, positionMs: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = _entries.value.firstOrNull { it.id == entryId } ?: return@withContext
            if (kotlin.math.abs(target.lastPositionMs - positionMs) < 1500L && positionMs > 0L) {
                return@withContext
            }
            val updated = _entries.value.map {
                if (it.id == entryId) it.copy(lastPositionMs = positionMs.coerceAtLeast(0L)) else it
            }
            _entries.value = updated
            saveToDisk(updated)
        }
    }

    suspend fun deleteEntry(entryId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = _entries.value.firstOrNull { it.id == entryId } ?: return@withContext
            File(target.filePath).delete()
            target.subtitlePath?.let { File(it).delete() }
            val updated = _entries.value.filterNot { it.id == entryId }
            _entries.value = updated
            saveToDisk(updated)

            val updatedPlaylists = _playlists.value.map { pl ->
                if (entryId in pl.entryIds) pl.copy(entryIds = pl.entryIds.filterNot { it == entryId }) else pl
            }
            _playlists.value = updatedPlaylists
            savePlaylistsToDisk(updatedPlaylists)
        }
    }

    suspend fun createPlaylist(name: String): UserPlaylist? = withContext(Dispatchers.IO) {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return@withContext null
        mutex.withLock {
            val pl = UserPlaylist(
                id = UUID.randomUUID().toString(),
                name = cleaned,
                entryIds = emptyList(),
                createdAtMillis = System.currentTimeMillis(),
            )
            val updated = listOf(pl) + _playlists.value
            _playlists.value = updated
            savePlaylistsToDisk(updated)
            pl
        }
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _playlists.value.filterNot { it.id == playlistId }
            _playlists.value = updated
            savePlaylistsToDisk(updated)
        }
    }

    suspend fun addEntryToPlaylist(playlistId: String, entryId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _playlists.value.map { pl ->
                if (pl.id == playlistId && entryId !in pl.entryIds) {
                    pl.copy(entryIds = pl.entryIds + entryId)
                } else {
                    pl
                }
            }
            _playlists.value = updated
            savePlaylistsToDisk(updated)
        }
    }

    suspend fun removeEntryFromPlaylist(playlistId: String, entryId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _playlists.value.map { pl ->
                if (pl.id == playlistId) {
                    pl.copy(entryIds = pl.entryIds.filterNot { it == entryId })
                } else {
                    pl
                }
            }
            _playlists.value = updated
            savePlaylistsToDisk(updated)
        }
    }

    fun getShareUri(entry: MediaEntry): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(entry.filePath),
        )
    }

    private fun mimeTypeFor(ext: String): String = when (ext.lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "opus" -> "audio/ogg"
        else -> "video/mp4"
    }

    private fun exportToSafFolder(file: File, ext: String, treeUriString: String): Boolean {
        return try {
            val treeUri = Uri.parse(treeUriString)
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val newDocUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                mimeTypeFor(ext),
                file.name,
            ) ?: return false
            context.contentResolver.openOutputStream(newDocUri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            } ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun exportToMediaStore(file: File, ext: String, isAudio: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val mimeType = mimeTypeFor(ext)
        val collection = if (isAudio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relativeDir = if (isAudio) {
            "${Environment.DIRECTORY_MUSIC}/YT Downloader"
        } else {
            "${Environment.DIRECTORY_MOVIES}/YT Downloader"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        try {
            val resolver = context.contentResolver
            val itemUri = resolver.insert(collection, values) ?: return
            resolver.openOutputStream(itemUri)?.use { out ->
                file.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
        } catch (_: Exception) {
            // Public export is best-effort; local library file remains playable.
        }
    }
}
