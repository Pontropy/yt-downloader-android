package com.pontropy.ytplayer.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.pontropy.ytplayer.player.EqPresetMode
import com.pontropy.ytplayer.player.PlayerController
import com.pontropy.ytplayer.player.PlayerUiState
import com.pontropy.ytplayer.ui.formatDurationMillis
import kotlinx.coroutines.flow.StateFlow

@Composable
fun MiniPlayerBar(
    state: PlayerUiState,
    positionMsFlow: StateFlow<Long>,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = state.currentEntry ?: return
    val positionMs by positionMsFlow.collectAsStateWithLifecycle()
    val progress = if (state.durationMs > 0L) {
        (positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand),
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!entry.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = entry.thumbnailUrl,
                            contentDescription = entry.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            if (entry.isAudioOnly) Icons.Default.MusicNote else Icons.Default.VideoFile,
                            contentDescription = null,
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${entry.extension} • ${formatDurationMillis(positionMs)} / ${formatDurationMillis(state.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Старт/Пауза",
                    )
                }
                if (state.hasNext) {
                    IconButton(onClick = onNext) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Следующий")
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть")
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalLayoutApi::class)
@OptIn(UnstableApi::class)
@Composable
fun FullPlayerOverlay(
    state: PlayerUiState,
    positionMsFlow: StateFlow<Long>,
    playerInstance: Player?,
    controller: PlayerController,
    isInPictureInPicture: Boolean,
    onEnterPip: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = state.currentEntry ?: return
    val context = LocalContext.current
    val activity = context as? Activity
    var isFullscreenLandscape by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showEqDialog by remember { mutableStateOf(false) }
    var showAudioTracksDialog by remember { mutableStateOf(false) }

    DisposableEffect(isFullscreenLandscape, isInPictureInPicture) {
        if (activity != null && !isInPictureInPicture) {
            activity.requestedOrientation = if (isFullscreenLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        onDispose {
            if (activity != null && isFullscreenLandscape) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    BackHandler(enabled = !isInPictureInPicture) {
        if (isFullscreenLandscape) {
            isFullscreenLandscape = false
        } else {
            onCollapse()
        }
    }

    // Minimal PiP surface without chrome
    if (isInPictureInPicture && !entry.isAudioOnly) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            VideoPlayerSurface(
                player = playerInstance,
                useController = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = if (!entry.isAudioOnly && isFullscreenLandscape) {
            Color.Black
        } else {
            MaterialTheme.colorScheme.background
        },
    ) {
        if (!entry.isAudioOnly && isFullscreenLandscape) {
            Box(modifier = Modifier.fillMaxSize()) {
                VideoPlayerSurface(
                    player = playerInstance,
                    useController = true,
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(
                    onClick = { isFullscreenLandscape = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                ) {
                    Icon(
                        Icons.Default.FullscreenExit,
                        contentDescription = "Выйти из полноэкранного режима",
                        tint = Color.White,
                    )
                }
            }
            return@Surface
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top bar with EQ, Timer, Subtitles, PiP, Speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Свернуть")
                }

                Text(
                    text = if (entry.isAudioOnly) "Аудиоплеер" else "Видеоплеер",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.audioTracks.size > 1) {
                        IconButton(
                            onClick = { showAudioTracksDialog = true },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.Audiotrack,
                                contentDescription = "Выбор аудиодорожки",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (!entry.isAudioOnly && state.subtitlesAvailable) {
                        IconButton(
                            onClick = { controller.toggleSubtitles() },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.Subtitles,
                                contentDescription = "Субтитры",
                                tint = if (state.subtitlesEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    IconButton(
                        onClick = { showEqDialog = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Эквалайзер",
                            tint = if (state.eqPreset != EqPresetMode.NORMAL || state.loudnessGainMb > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(
                        onClick = { showSleepDialog = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = "Таймер сна",
                            tint = if (state.sleepTimerRemainingMs != null || state.sleepAfterTrack) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    if (!entry.isAudioOnly) {
                        IconButton(
                            onClick = onEnterPip,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.PictureInPictureAlt,
                                contentDescription = "Картинка в картинке",
                            )
                        }
                    }
                    TextButton(
                        onClick = { controller.cycleSpeed() },
                        modifier = Modifier.defaultMinSize(minWidth = 42.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = formatSpeedLabel(state.playbackSpeed),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            // Artwork or Video Surface
            if (!entry.isAudioOnly) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                ) {
                    VideoPlayerSurface(
                        player = playerInstance,
                        useController = false,
                        onSurfaceTap = { controller.togglePlayPause() },
                        modifier = Modifier.fillMaxSize(),
                    )
                    IconButton(
                        onClick = { isFullscreenLandscape = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                    ) {
                        Icon(
                            Icons.Default.Fullscreen,
                            contentDescription = "На весь экран",
                            tint = Color.White,
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!entry.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = entry.thumbnailUrl,
                                contentDescription = entry.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(96.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // Track Metadata & Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = buildList {
                        if (entry.artist.isNotBlank()) add(entry.artist)
                        if (entry.extension.isNotBlank()) add(entry.extension)
                        if (entry.qualityLabel.isNotBlank() &&
                            !entry.qualityLabel.equals(entry.extension, ignoreCase = true)
                        ) {
                            add(entry.qualityLabel)
                        }
                        if (state.sleepTimerRemainingMs != null) {
                            add("Сон: ${formatDurationMillis(state.sleepTimerRemainingMs)}")
                        } else if (state.sleepAfterTrack) {
                            add("Сон после трека")
                        }
                    }.joinToString(" • ")
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Seek bar isolated on positionMsFlow so VideoPlayerSurface never recomposes on ticks
                PlayerSeekSection(
                    positionMsFlow = positionMsFlow,
                    durationMs = state.durationMs,
                    onSeekTo = { target -> controller.seekTo(target) },
                )

                // Transport buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { controller.toggleShuffle() }) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Перемешать",
                            tint = if (state.shuffleEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }

                    IconButton(onClick = { controller.skipPrevious() }) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Предыдущий",
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    FilledTonalIconButton(onClick = { controller.seekBy(-10_000L) }) {
                        Icon(Icons.Default.Replay10, contentDescription = "-10 сек")
                    }

                    FilledIconButton(
                        onClick = { controller.togglePlayPause() },
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Старт/Пауза",
                            modifier = Modifier.size(34.dp),
                        )
                    }

                    FilledTonalIconButton(onClick = { controller.seekBy(10_000L) }) {
                        Icon(Icons.Default.Forward10, contentDescription = "+10 сек")
                    }

                    IconButton(
                        onClick = { controller.skipNext() },
                        enabled = state.hasNext,
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Следующий",
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    IconButton(onClick = { controller.toggleRepeatMode() }) {
                        val (repIcon, active) = when (state.repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne to true
                            Player.REPEAT_MODE_ALL -> Icons.Default.Repeat to true
                            else -> Icons.Default.Repeat to false
                        }
                        Icon(
                            repIcon,
                            contentDescription = "Повтор",
                            tint = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Таймер сна") },
            text = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.sleepTimerRemainingMs == null && !state.sleepAfterTrack,
                        onClick = {
                            controller.setSleepTimerMinutes(null)
                            showSleepDialog = false
                        },
                        label = { Text("Выкл") },
                    )
                    listOf(15, 30, 60).forEach { mins ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                controller.setSleepTimerMinutes(mins)
                                showSleepDialog = false
                            },
                            label = { Text("$mins мин") },
                        )
                    }
                    FilterChip(
                        selected = state.sleepAfterTrack,
                        onClick = {
                            controller.setSleepAfterCurrentTrack(!state.sleepAfterTrack)
                            showSleepDialog = false
                        },
                        label = { Text("До конца трека") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepDialog = false }) {
                    Text("Закрыть")
                }
            },
        )
    }

    if (showEqDialog) {
        AlertDialog(
            onDismissRequest = { showEqDialog = false },
            title = { Text("Звук и эквалайзер") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EqPresetMode.entries.forEach { preset ->
                            FilterChip(
                                selected = state.eqPreset == preset,
                                onClick = { controller.setEqPreset(preset) },
                                label = { Text(preset.label) },
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Усиление громкости: +${state.loudnessGainMb / 100} дБ",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = state.loudnessGainMb.toFloat(),
                            onValueChange = { controller.setLoudnessGainMb(it.toInt()) },
                            valueRange = 0f..1500f,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEqDialog = false }) {
                    Text("Готово")
                }
            },
        )
    }

    if (showAudioTracksDialog) {
        AlertDialog(
            onDismissRequest = { showAudioTracksDialog = false },
            title = { Text("Встроенные аудиодорожки") },
            text = {
                Column {
                    state.audioTracks.forEach { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    controller.selectInternalAudioTrack(track)
                                    showAudioTracksDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = track.isSelected,
                                onClick = {
                                    controller.selectInternalAudioTrack(track)
                                    showAudioTracksDialog = false
                                },
                            )
                            Text(track.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioTracksDialog = false }) {
                    Text("Закрыть")
                }
            },
        )
    }
}

@Composable
private fun PlayerSeekSection(
    positionMsFlow: StateFlow<Long>,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
) {
    val positionMs by positionMsFlow.collectAsStateWithLifecycle()
    val duration = durationMs.coerceAtLeast(1L)
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val sliderValue = if (isDragging) {
        dragProgress
    } else {
        (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    Column {
        Slider(
            value = sliderValue,
            onValueChange = { value ->
                isDragging = true
                dragProgress = value
            },
            onValueChangeFinished = {
                onSeekTo((dragProgress * duration).toLong())
                isDragging = false
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDurationMillis(
                    if (isDragging) (dragProgress * duration).toLong() else positionMs
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDurationMillis(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerSurface(
    player: Player?,
    useController: Boolean,
    modifier: Modifier = Modifier,
    onSurfaceTap: (() -> Unit)? = null,
) {
    val latestOnTap by rememberUpdatedState(onSurfaceTap)
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setKeepContentOnPlayerReset(true)
                this.useController = useController
                if (!useController) {
                    hideController()
                    setOnClickListener { latestOnTap?.invoke() }
                }
                this.player = player
            }
        },
        update = { view ->
            if (view.useController != useController) {
                view.useController = useController
                if (!useController) {
                    view.hideController()
                    view.setOnClickListener { latestOnTap?.invoke() }
                } else {
                    view.setOnClickListener(null)
                    view.isClickable = false
                }
            }
            if (view.player !== player) {
                view.player = player
            }
        },
        modifier = modifier,
    )
}

private fun formatSpeedLabel(speed: Float): String {
    val rounded = kotlin.math.round(speed * 100f) / 100f
    return if (rounded == rounded.toInt().toFloat()) {
        "${rounded.toInt()}.0x"
    } else {
        "${rounded.toString().trimEnd('0').trimEnd('.')}x"
    }
}
