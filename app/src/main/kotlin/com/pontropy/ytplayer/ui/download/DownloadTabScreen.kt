package com.pontropy.ytplayer.ui.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pontropy.ytplayer.EngineInitState
import com.pontropy.ytplayer.data.AppSettings
import com.pontropy.ytplayer.data.AudioLanguages
import com.pontropy.ytplayer.data.DownloadProgressState
import com.pontropy.ytplayer.data.PreviewState
import com.pontropy.ytplayer.data.QueueItem
import com.pontropy.ytplayer.data.QueueItemStatus
import com.pontropy.ytplayer.data.UrlValidator
import com.pontropy.ytplayer.ui.formatDurationSeconds
import com.pontropy.ytplayer.ui.theme.LocalExtraAppColors

@Composable
fun DownloadTabScreen(
    urlText: String,
    onUrlChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onRemoveQueueItem: (String) -> Unit,
    onClearQueue: () -> Unit,
    onOpenSettings: () -> Unit,
    settings: AppSettings,
    engineState: EngineInitState,
    previewState: PreviewState,
    progressState: DownloadProgressState,
    queue: List<QueueItem>,
    modifier: Modifier = Modifier,
) {
    val extraColors = LocalExtraAppColors.current
    var showLogs by rememberSaveable { mutableStateOf(false) }
    val activeOrPendingCount = queue.count {
        it.status == QueueItemStatus.PENDING || it.status == QueueItemStatus.DOWNLOADING
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Engine readiness banner
        if (engineState !is EngineInitState.Ready) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (engineState is EngineInitState.Error) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (engineState is EngineInitState.Initializing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                "Подготовка yt-dlp и ffmpeg...",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else if (engineState is EngineInitState.Error) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null)
                            Text(
                                "Ошибка движка: ${engineState.message}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        // Active Video & Music Settings Summary Bar
        item {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSettings),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Проверка форматов — при каждом скачивании",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Дорожки: ${AudioLanguages.shortLabel(settings.videoAudioCode)}/${AudioLanguages.shortLabel(settings.musicAudioCode)} • Потоков: ${settings.maxConcurrentDownloads}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Настройки",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // URL Input & Action Buttons
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = onUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ссылка на YouTube, YT Music или плейлист") },
                        placeholder = { Text("https://youtu.be/... или music.youtube.com/...") },
                        maxLines = 3,
                        trailingIcon = {
                            if (urlText.isNotEmpty()) {
                                IconButton(onClick = { onUrlChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onDownloadClick() }),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = onPasteClick,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Вставить", maxLines = 1, softWrap = false)
                        }

                        Button(
                            onClick = onDownloadClick,
                            modifier = Modifier.weight(1.25f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            enabled = UrlValidator.parseUrls(urlText).isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (progressState.isActive) "Добавить" else "Скачать",
                                maxLines = 1,
                                softWrap = false,
                            )
                        }

                        if (progressState.isActive) {
                            FilledTonalButton(
                                onClick = onCancelClick,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Отмена")
                            }
                        }
                    }
                }
            }
        }

        // Metadata Preview Card
        if (previewState !is PreviewState.Idle) {
            item {
                PreviewCard(previewState = previewState)
            }
        }

        // Real-time Progress & Logs Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = progressState.statusText,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (progressState.etaSeconds > 0 && progressState.isActive) {
                            Text(
                                text = "ETA ${formatDurationSeconds(progressState.etaSeconds.toInt())}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    if (progressState.isActive) {
                        val pct = progressState.progressPercent
                        if (pct != null) {
                            LinearProgressIndicator(
                                progress = { (pct / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = extraColors.success,
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                            )
                        }
                    } else {
                        LinearProgressIndicator(
                            progress = {
                                if (progressState.progressPercent == 100f) 1f else 0f
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = extraColors.success,
                        )
                    }

                    if (progressState.logs.isNotEmpty()) {
                        TextButton(
                            onClick = { showLogs = !showLogs },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        ) {
                            Text("Журнал (${progressState.logs.size})")
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (showLogs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        AnimatedVisibility(visible = showLogs) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    progressState.logs.takeLast(12).forEach { line ->
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Download Queue Header
        if (queue.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Очередь ($activeOrPendingCount/${queue.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = onClearQueue) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Очистить")
                    }
                }
            }

            items(queue, key = { it.id }) { item ->
                QueueItemCard(
                    item = item,
                    onRemove = { onRemoveQueueItem(item.id) },
                )
            }
        }
    }
}

@Composable
private fun PreviewCard(previewState: PreviewState) {
    val extraColors = LocalExtraAppColors.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        when (previewState) {
            is PreviewState.Loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Загрузка превью и языков...", style = MaterialTheme.typography.bodyMedium)
                }
            }

            is PreviewState.Loaded -> {
                val meta = previewState.preview
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 96.dp, height = 64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!meta.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = meta.thumbnailUrl,
                                contentDescription = meta.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                if (meta.isMusic) Icons.Default.MusicNote else Icons.Default.VideoFile,
                                contentDescription = null,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = meta.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subtitle = buildList {
                            if (meta.artist.isNotBlank()) add(meta.artist)
                            if (meta.durationSeconds > 0) add(formatDurationSeconds(meta.durationSeconds))
                            if (!meta.isMusic && meta.maxHeight != null) add("до ${meta.maxHeight}p")
                        }.joinToString(" • ")
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val langs = meta.availableAudioTracks
                            .filter { it.code != AudioLanguages.ORIGINAL }
                            .joinToString(", ") { it.code }
                        val audioHint = when {
                            langs.isNotBlank() -> "Дорожки: $langs"
                            meta.hasRussianAudio -> "Озвучка RU: обнаружена"
                            else -> "Озвучка RU: не указана (откат /ba)"
                        }
                        Text(
                            text = audioHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (meta.hasRussianAudio || langs.isNotBlank()) extraColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            is PreviewState.Error -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = extraColors.warning,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Превью недоступно (${previewState.message}) — скачивание всё равно доступно",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            PreviewState.Idle -> Unit
        }
    }
}

@Composable
private fun QueueItemCard(
    item: QueueItem,
    onRemove: () -> Unit,
) {
    val extraColors = LocalExtraAppColors.current
    val (icon, tint) = when (item.status) {
        QueueItemStatus.PENDING -> Icons.Default.HourglassTop to MaterialTheme.colorScheme.onSurfaceVariant
        QueueItemStatus.DOWNLOADING -> Icons.Default.PlayArrow to MaterialTheme.colorScheme.primary
        QueueItemStatus.DONE -> Icons.Default.CheckCircle to extraColors.success
        QueueItemStatus.ERROR -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
        QueueItemStatus.CANCELLED -> Icons.Default.Stop to extraColors.warning
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val metaText = buildString {
                    append(item.quality.shortLabel)
                    append(" • ")
                    append(AudioLanguages.shortLabel(item.audioCode))
                    if (item.status == QueueItemStatus.DOWNLOADING && item.stageLabel.isNotBlank()) {
                        append(" • ")
                        append(item.stageLabel)
                    } else if (item.durationSeconds > 0) {
                        append(" • ")
                        append(formatDurationSeconds(item.durationSeconds))
                    }
                    if (item.errorMessage != null) {
                        append(" • ")
                        append(item.errorMessage)
                    }
                }
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == QueueItemStatus.ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (item.status != QueueItemStatus.DOWNLOADING) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Убрать",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
