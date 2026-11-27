package com.pontropy.ytplayer.player

import android.content.ComponentName
import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.pontropy.ytplayer.data.MediaEntry
import com.pontropy.ytplayer.data.MediaLibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class EqPresetMode(val label: String) {
    NORMAL("Обычный"),
    BASS_BOOST("Бас +"),
    VOCAL("Вокал"),
    ROCK("Рок"),
    ELECTRONIC("Электроника"),
}

data class InternalAudioTrack(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean,
)

data class PlayerUiState(
    val currentEntry: MediaEntry? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val subtitlesAvailable: Boolean = false,
    val subtitlesEnabled: Boolean = true,
    val audioTracks: List<InternalAudioTrack> = emptyList(),
    val sleepTimerRemainingMs: Long? = null,
    val sleepAfterTrack: Boolean = false,
    val eqPreset: EqPresetMode = EqPresetMode.NORMAL,
    val loudnessGainMb: Int = 0,
)

class PlayerController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val libraryRepository: MediaLibraryRepository,
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var playlistEntries: MutableList<MediaEntry> = mutableListOf()
    private var positionTickerJob: Job? = null
    private var sleepTickerJob: Job? = null

    private var eqPreset: EqPresetMode = EqPresetMode.NORMAL
    private var loudnessGainMb: Int = 0
    private var subtitlesEnabled: Boolean = true
    private var sleepRemainingMs: Long? = null
    private var sleepAfterTrack: Boolean = false
    private var lastTransitionEntryId: String? = null

    private var boundAudioSessionId: Int = 0
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMsFlow: StateFlow<Long> = _positionMs.asStateFlow()

    private val _playerInstance = MutableStateFlow<Player?>(null)
    val playerInstance: StateFlow<Player?> = _playerInstance.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncStateFromPlayer(player)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (sleepAfterTrack && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                mediaController?.pause()
                sleepAfterTrack = false
                mediaController?.let { syncStateFromPlayer(it) }
            }
            lastTransitionEntryId = mediaItem?.mediaId
            ensureAudioEffectsAttached()
        }

        override fun onTracksChanged(tracks: Tracks) {
            mediaController?.let { syncStateFromPlayer(it) }
        }
    }

    init {
        scope.launch {
            PlaybackService.activeExoPlayer.collect { exo ->
                if (exo != null) {
                    attachEffectsForSession(exo.audioSessionId)
                }
            }
        }
    }

    private fun ensureConnected(onReady: (MediaController) -> Unit) {
        mediaController?.let { existing ->
            onReady(existing)
            return
        }

        val existingFuture = controllerFuture
        if (existingFuture != null) {
            existingFuture.addListener(
                { runCatching { existingFuture.get() }.getOrNull()?.let(onReady) },
                ContextCompat.getMainExecutor(context),
            )
            return
        }

        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
                mediaController = controller
                _playerInstance.value = controller
                controller.addListener(playerListener)
                syncStateFromPlayer(controller)
                startPositionTicker()
                onReady(controller)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun buildMediaItem(entry: MediaEntry): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(entry.title)
            .setArtist(entry.artist.ifBlank { entry.qualityLabel })
            .apply {
                entry.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { thumb ->
                    setArtworkUri(Uri.parse(thumb))
                }
            }
            .build()

        val builder = MediaItem.Builder()
            .setMediaId(entry.id)
            .setUri(Uri.fromFile(File(entry.filePath)))
            .setMediaMetadata(metadata)

        val subFile = entry.subtitlePath?.let { File(it) }
        if (subFile != null && subFile.exists()) {
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subFile))
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage("ru")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            builder.setSubtitleConfigurations(listOf(subConfig))
        }
        return builder.build()
    }

    fun playPlaylist(entries: List<MediaEntry>, startIndex: Int) {
        if (entries.isEmpty() || startIndex !in entries.indices) return
        ensureConnected { controller ->
            playlistEntries = entries.toMutableList()
            val mediaItems = entries.map { buildMediaItem(it) }
            val startEntry = entries[startIndex]
            val resumePos = if (startEntry.durationSeconds >= 180 && startEntry.lastPositionMs in 5000L until ((startEntry.durationSeconds * 1000L) - 8000L)) {
                startEntry.lastPositionMs
            } else {
                0L
            }
            controller.setMediaItems(mediaItems, startIndex, resumePos)
            applySubtitleVisibility(controller, subtitlesEnabled)
            controller.prepare()
            controller.play()
            syncStateFromPlayer(controller)
            ensureAudioEffectsAttached()
        }
    }

    fun playNext(entry: MediaEntry) {
        val controller = mediaController
        if (controller == null || controller.mediaItemCount == 0) {
            playPlaylist(listOf(entry), 0)
            return
        }
        val insertIdx = (controller.currentMediaItemIndex + 1).coerceIn(0, playlistEntries.size)
        playlistEntries.add(insertIdx, entry)
        controller.addMediaItem(insertIdx, buildMediaItem(entry))
        syncStateFromPlayer(controller)
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        val clamped = positionMs.coerceAtLeast(0L)
        controller.seekTo(clamped)
        _positionMs.value = clamped
        syncStateFromPlayer(controller)
    }

    fun seekBy(deltaMs: Long) {
        val controller = mediaController ?: return
        val maxDur = controller.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = (controller.currentPosition + deltaMs).coerceIn(0L, maxDur)
        controller.seekTo(target)
        _positionMs.value = target
        syncStateFromPlayer(controller)
    }

    fun skipNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3000L || !controller.hasPreviousMediaItem()) {
            controller.seekTo(0L)
        } else {
            controller.seekToPreviousMediaItem()
        }
    }

    fun cycleSpeed() {
        val controller = mediaController ?: return
        val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.75f)
        val current = controller.playbackParameters.speed
        val idx = speeds.indexOfFirst { kotlin.math.abs(it - current) < 0.05f }
        val next = speeds[(idx + 1) % speeds.size]
        controller.playbackParameters = PlaybackParameters(next)
        syncStateFromPlayer(controller)
    }

    fun toggleRepeatMode() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        syncStateFromPlayer(controller)
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
        syncStateFromPlayer(controller)
    }

    fun toggleSubtitles() {
        val controller = mediaController ?: return
        subtitlesEnabled = !subtitlesEnabled
        applySubtitleVisibility(controller, subtitlesEnabled)
        syncStateFromPlayer(controller)
    }

    fun selectInternalAudioTrack(track: InternalAudioTrack) {
        val controller = mediaController ?: return
        val group = controller.currentTracks.groups.getOrNull(track.groupIndex) ?: return
        controller.trackSelectionParameters = controller.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(track.trackIndex)))
            .build()
        syncStateFromPlayer(controller)
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        sleepTickerJob?.cancel()
        sleepAfterTrack = false
        if (minutes == null || minutes <= 0) {
            sleepRemainingMs = null
            mediaController?.let { syncStateFromPlayer(it) } ?: run {
                _state.value = _state.value.copy(sleepTimerRemainingMs = null, sleepAfterTrack = false)
            }
            return
        }
        sleepRemainingMs = minutes * 60_000L
        mediaController?.let { syncStateFromPlayer(it) } ?: run {
            _state.value = _state.value.copy(sleepTimerRemainingMs = sleepRemainingMs, sleepAfterTrack = false)
        }
        sleepTickerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val rem = (sleepRemainingMs ?: 0L) - 1000L
                if (rem <= 0L) {
                    sleepRemainingMs = null
                    mediaController?.pause()
                    mediaController?.let { syncStateFromPlayer(it) }
                    break
                } else {
                    sleepRemainingMs = rem
                    _state.value = _state.value.copy(sleepTimerRemainingMs = rem)
                }
            }
        }
    }

    fun setSleepAfterCurrentTrack(enabled: Boolean) {
        sleepTickerJob?.cancel()
        sleepRemainingMs = null
        sleepAfterTrack = enabled
        mediaController?.let { syncStateFromPlayer(it) } ?: run {
            _state.value = _state.value.copy(sleepTimerRemainingMs = null, sleepAfterTrack = enabled)
        }
    }

    fun setEqPreset(preset: EqPresetMode) {
        eqPreset = preset
        applyEqualizerPreset(preset)
        mediaController?.let { syncStateFromPlayer(it) } ?: run {
            _state.value = _state.value.copy(eqPreset = preset)
        }
    }

    fun setLoudnessGainMb(gainMb: Int) {
        loudnessGainMb = gainMb.coerceIn(0, 1500)
        applyLoudnessGain(loudnessGainMb)
        mediaController?.let { syncStateFromPlayer(it) } ?: run {
            _state.value = _state.value.copy(loudnessGainMb = loudnessGainMb)
        }
    }

    fun stopAndClear() {
        sleepTickerJob?.cancel()
        sleepRemainingMs = null
        sleepAfterTrack = false
        _positionMs.value = 0L
        val controller = mediaController ?: return
        controller.stop()
        controller.clearMediaItems()
        playlistEntries.clear()
        _state.value = PlayerUiState(eqPreset = eqPreset, loudnessGainMb = loudnessGainMb)
    }

    private fun applySubtitleVisibility(player: Player, enabled: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
    }

    private fun ensureAudioEffectsAttached() {
        val exo: ExoPlayer? = PlaybackService.activeExoPlayer.value
        if (exo != null && exo.audioSessionId != 0 && exo.audioSessionId != boundAudioSessionId) {
            attachEffectsForSession(exo.audioSessionId)
        }
    }

    private fun attachEffectsForSession(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == boundAudioSessionId) return
        releaseAudioEffects()
        boundAudioSessionId = audioSessionId
        runCatching {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = eqPreset != EqPresetMode.NORMAL
            }
            applyEqualizerPreset(eqPreset)
        }
        runCatching {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(loudnessGainMb)
                enabled = loudnessGainMb > 0
            }
        }
    }

    private fun applyEqualizerPreset(preset: EqPresetMode) {
        val eq = equalizer ?: return
        runCatching {
            if (preset == EqPresetMode.NORMAL) {
                eq.enabled = false
                return
            }
            eq.enabled = true
            val bands = eq.numberOfBands.toInt()
            val minLv = eq.bandLevelRange[0].toInt()
            val maxLv = eq.bandLevelRange[1].toInt()
            for (b in 0 until bands) {
                val ratio = when (preset) {
                    EqPresetMode.BASS_BOOST -> when {
                        b == 0 -> 0.85f
                        b == 1 -> 0.55f
                        else -> 0.0f
                    }
                    EqPresetMode.VOCAL -> when {
                        b in 1..(bands - 2) -> 0.45f
                        else -> -0.15f
                    }
                    EqPresetMode.ROCK -> when {
                        b == 0 || b == bands - 1 -> 0.55f
                        else -> -0.1f
                    }
                    EqPresetMode.ELECTRONIC -> when {
                        b == 0 -> 0.7f
                        b == bands - 1 -> 0.6f
                        else -> 0.1f
                    }
                    EqPresetMode.NORMAL -> 0.0f
                }
                val level = if (ratio >= 0f) {
                    (ratio * maxLv).toInt()
                } else {
                    (-ratio * minLv).toInt()
                }.coerceIn(minLv, maxLv).toShort()
                eq.setBandLevel(b.toShort(), level)
            }
        }
    }

    private fun applyLoudnessGain(gainMb: Int) {
        val enhancer = loudnessEnhancer ?: return
        runCatching {
            enhancer.setTargetGain(gainMb)
            enhancer.enabled = gainMb > 0
        }
    }

    private fun releaseAudioEffects() {
        runCatching { equalizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        loudnessEnhancer = null
        boundAudioSessionId = 0
    }

    private fun startPositionTicker() {
        if (positionTickerJob?.isActive == true) return
        positionTickerJob = scope.launch {
            var persistTickCounter = 0
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        val pos = controller.currentPosition.coerceAtLeast(0L)
                        _positionMs.value = pos
                        val knownDur = controller.duration.takeIf { it > 0 } ?: 0L
                        if (knownDur != _state.value.durationMs && knownDur > 0L) {
                            _state.value = _state.value.copy(durationMs = knownDur)
                        }
                        persistTickCounter++
                        if (persistTickCounter % 120 == 0) {
                            val currentEntry = _state.value.currentEntry
                            val dur = controller.duration
                            if (currentEntry != null && dur >= 180_000L) {
                                scope.launch {
                                    libraryRepository.updatePlaybackPosition(
                                        currentEntry.id,
                                        pos,
                                    )
                                }
                            }
                        }
                    }
                }
                delay(16L)
            }
        }
    }

    private fun syncStateFromPlayer(player: Player) {
        val currentId = player.currentMediaItem?.mediaId
        val entry = playlistEntries.firstOrNull { it.id == currentId }
            ?: playlistEntries.getOrNull(player.currentMediaItemIndex)

        var subsAvailable = entry?.subtitlePath?.let { File(it).exists() } == true
        val audioTracks = mutableListOf<InternalAudioTrack>()
        player.currentTracks.groups.forEachIndexed { gIdx, group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                subsAvailable = true
            } else if (group.type == C.TRACK_TYPE_AUDIO) {
                for (tIdx in 0 until group.length) {
                    val fmt = group.getTrackFormat(tIdx)
                    val lang = fmt.language?.takeIf { it.isNotBlank() }
                    val label = buildString {
                        append(fmt.label ?: lang?.uppercase() ?: "Дорожка ${audioTracks.size + 1}")
                        if (lang != null && fmt.label != null && !fmt.label!!.contains(lang, ignoreCase = true)) {
                            append(" ($lang)")
                        }
                    }
                    audioTracks.add(
                        InternalAudioTrack(
                            groupIndex = gIdx,
                            trackIndex = tIdx,
                            label = label,
                            isSelected = group.isTrackSelected(tIdx),
                        )
                    )
                }
            }
        }

        val pos = player.currentPosition.coerceAtLeast(0L)
        _positionMs.value = pos
        _state.value = PlayerUiState(
            currentEntry = entry,
            isPlaying = player.isPlaying,
            positionMs = pos,
            durationMs = player.duration.takeIf { it > 0 } ?: 0L,
            playbackSpeed = player.playbackParameters.speed,
            repeatMode = player.repeatMode,
            shuffleEnabled = player.shuffleModeEnabled,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            subtitlesAvailable = subsAvailable,
            subtitlesEnabled = subtitlesEnabled,
            audioTracks = audioTracks,
            sleepTimerRemainingMs = sleepRemainingMs,
            sleepAfterTrack = sleepAfterTrack,
            eqPreset = eqPreset,
            loudnessGainMb = loudnessGainMb,
        )
    }
}
