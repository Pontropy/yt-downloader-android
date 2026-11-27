package com.pontropy.ytplayer.ui.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pontropy.ytplayer.data.AppSettings
import com.pontropy.ytplayer.data.AudioLanguages
import com.pontropy.ytplayer.data.AudioTrackOption
import com.pontropy.ytplayer.data.PendingDownloadSpec
import com.pontropy.ytplayer.data.PlaylistLoadState
import com.pontropy.ytplayer.data.PreviewState
import com.pontropy.ytplayer.data.QualityPreset
import com.pontropy.ytplayer.data.UrlValidator
import com.pontropy.ytplayer.ui.formatDurationSeconds

const val PICKER_AUDIO_FROM_SETTINGS = "__use_settings__"

@kotlin.OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadPickerSheet(
    urls: List<String>,
    settings: AppSettings,
    previewState: PreviewState,
    playlistState: PlaylistLoadState,
    onLoadPlaylist: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (specs: List<PendingDownloadSpec>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val loadedPreview = (previewState as? PreviewState.Loaded)?.preview?.takeIf {
        urls.size == 1 && it.url == urls.first()
    }
    val hasPlaylistUrl = urls.size == 1 && UrlValidator.isPlaylistUrl(urls.first())
    val playlistLoaded = playlistState as? PlaylistLoadState.Loaded

    LaunchedEffect(urls) {
        if (urls.size == 1 && UrlValidator.isPurePlaylistUrl(urls.first()) && playlistState is PlaylistLoadState.Idle) {
            onLoadPlaylist(urls.first())
        }
    }

    val isProbing = urls.size == 1 &&
        playlistLoaded == null &&
        (previewState is PreviewState.Loading || (previewState is PreviewState.Idle && !hasPlaylistUrl))

    val availableVideo = remember(loadedPreview, playlistLoaded) {
        when {
            playlistLoaded != null -> QualityPreset.videoEntries
            loadedPreview != null -> loadedPreview.availableVideoPresets
            else -> QualityPreset.videoEntries
        }
    }
    val availableMusic = remember(loadedPreview, playlistLoaded) {
        when {
            playlistLoaded != null -> QualityPreset.musicEntries
            loadedPreview != null -> loadedPreview.availableMusicPresets
            else -> QualityPreset.musicEntries
        }
    }

    var selectedVideo by remember(urls) { mutableStateOf<Set<QualityPreset>>(emptySet()) }
    var selectedMusic by remember(urls) { mutableStateOf<Set<QualityPreset>>(emptySet()) }
    var selectedAudio by remember(urls) { mutableStateOf(PICKER_AUDIO_FROM_SETTINGS) }
    var downloadSubs by remember(urls) { mutableStateOf(settings.downloadSubtitles) }
    var selectedPlaylistIds by remember(playlistState) {
        val loaded = playlistState as? PlaylistLoadState.Loaded
        mutableStateOf(loaded?.entries?.map { it.id }?.toSet() ?: emptySet())
    }

    LaunchedEffect(urls, loadedPreview, playlistLoaded) {
        val validV = availableVideo.toSet()
        val validM = availableMusic.toSet()
        var nextV = selectedVideo.intersect(validV)
        var nextM = selectedMusic.intersect(validM)
        if (nextV.isEmpty() && nextM.isEmpty() && !isProbing) {
            if (loadedPreview?.isMusic == true || availableVideo.isEmpty()) {
                val pickM = QualityPreset.AUDIO_M4A.takeIf { it in validM } ?: availableMusic.firstOrNull()
                if (pickM != null) nextM = setOf(pickM)
            } else if (availableVideo.isNotEmpty()) {
                val pickV = QualityPreset.VIDEO_MAX.takeIf { it in validV } ?: availableVideo.lastOrNull()
                if (pickV != null) nextV = setOf(pickV)
            }
        }
        selectedVideo = nextV
        selectedMusic = nextM
    }

    val audioOptions = remember(loadedPreview, settings) {
        val list = mutableListOf<AudioTrackOption>()
        val seen = mutableSetOf<String>()
        loadedPreview?.availableAudioTracks?.forEach { track ->
            if (seen.add(track.code)) list.add(track)
        }
        listOf(AudioLanguages.ORIGINAL, AudioLanguages.RUSSIAN, "en").forEach { code ->
            if (seen.add(code)) {
                list.add(AudioTrackOption(code = code, label = AudioLanguages.formatLabel(code)))
            }
        }
        list
    }

    val chosenPresets = remember(selectedVideo, selectedMusic) {
        QualityPreset.entries.filter { it in selectedVideo || it in selectedMusic }
    }
    val targetCount = if (playlistState is PlaylistLoadState.Loaded) {
        selectedPlaylistIds.size
    } else {
        urls.size
    }
    val totalTasks = targetCount * chosenPresets.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Доступные форматы и дорожки",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = loadedPreview?.title
                    ?: if (urls.size == 1) urls.first() else "Выбрано ссылок: ${urls.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (isProbing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        "Проверяем доступные форматы видео, музыки и дорожки...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            } else {
                Text(
                    text = when {
                        playlistLoaded != null -> "Плейлист: выберите треки и форматы"
                        loadedPreview != null && loadedPreview.formatsProbed -> {
                            val parts = buildList {
                                loadedPreview.maxHeight?.let { add("видео до ${it}p") }
                                if (availableMusic.isNotEmpty()) add("аудио: да")
                                val langs = loadedPreview.availableAudioTracks
                                    .filter { it.code != AudioLanguages.ORIGINAL }
                                    .joinToString(", ") { it.code }
                                if (langs.isNotBlank()) add("языки: $langs")
                            }
                            parts.joinToString(" • ").ifBlank { "Форматы проверены" }
                        }
                        previewState is PreviewState.Error -> "Не удалось проверить метаданные — показаны все форматы"
                        else -> "Выберите форматы видео и/или музыки"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Video formats
            Text(
                text = "Видео (YouTube)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (isProbing) {
                Text(
                    "Определяем доступные разрешения...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (availableVideo.isEmpty()) {
                Text(
                    "Видеодорожки не обнаружены (только аудио)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    availableVideo.forEach { preset ->
                        val checked = preset in selectedVideo
                        val note = loadedPreview?.videoFormatNotes?.get(preset)
                        val chipLabel = if (preset == QualityPreset.VIDEO_MAX && !note.isNullOrBlank()) {
                            "Макс ($note)"
                        } else {
                            preset.shortLabel
                        }
                        FilterChip(
                            selected = checked,
                            onClick = {
                                selectedVideo = if (checked) selectedVideo - preset else selectedVideo + preset
                            },
                            leadingIcon = if (checked) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            label = { Text(chipLabel) },
                        )
                    }
                }
            }

            // Music formats
            Text(
                text = "Музыка (YouTube Music / Аудио)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (isProbing) {
                Text(
                    "Определяем доступные аудиопотоки...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (availableMusic.isEmpty()) {
                Text(
                    "Аудиодорожки не обнаружены",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    availableMusic.forEach { preset ->
                        val checked = preset in selectedMusic
                        val note = loadedPreview?.musicFormatNotes?.get(preset)
                        val chipLabel = if (!note.isNullOrBlank()) {
                            "${preset.shortLabel} ($note)"
                        } else {
                            preset.shortLabel
                        }
                        FilterChip(
                            selected = checked,
                            onClick = {
                                selectedMusic = if (checked) selectedMusic - preset else selectedMusic + preset
                            },
                            leadingIcon = if (checked) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            label = { Text(chipLabel) },
                        )
                    }
                }
            }

            HorizontalDivider()

            // Dynamic Audio track language selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = if (loadedPreview != null && loadedPreview.availableAudioTracks.isNotEmpty()) {
                        "Язык аудиодорожки (доступно у видео: ${loadedPreview.availableAudioTracks.size})"
                    } else {
                        "Язык аудиодорожки"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = selectedAudio == PICKER_AUDIO_FROM_SETTINGS,
                    onClick = { selectedAudio = PICKER_AUDIO_FROM_SETTINGS },
                    label = {
                        Text(
                            "Из настроек (${AudioLanguages.shortLabel(settings.videoAudioCode)}/${AudioLanguages.shortLabel(settings.musicAudioCode)})"
                        )
                    },
                )
                audioOptions.forEach { track ->
                    FilterChip(
                        selected = selectedAudio == track.code,
                        onClick = { selectedAudio = track.code },
                        label = { Text(track.label) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Скачивать субтитры (.srt, для видео)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedVideo.isNotEmpty()) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Switch(
                    checked = downloadSubs,
                    onCheckedChange = { downloadSubs = it },
                    enabled = selectedVideo.isNotEmpty(),
                )
            }

            // Playlist / Album expansion
            if (hasPlaylistUrl) {
                HorizontalDivider()
                when (playlistState) {
                    PlaylistLoadState.Idle -> {
                        OutlinedButton(
                            onClick = { onLoadPlaylist(urls.first()) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.PlaylistPlay, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Загрузить весь плейлист / альбом")
                        }
                    }

                    is PlaylistLoadState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Загружаем список треков плейлиста...")
                        }
                    }

                    is PlaylistLoadState.Error -> {
                        Text(
                            text = "Плейлист: ${playlistState.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    is PlaylistLoadState.Loaded -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${playlistState.playlistTitle} (${selectedPlaylistIds.size}/${playlistState.entries.size})",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(
                                onClick = {
                                    selectedPlaylistIds = if (selectedPlaylistIds.size == playlistState.entries.size) {
                                        emptySet()
                                    } else {
                                        playlistState.entries.map { it.id }.toSet()
                                    }
                                }
                            ) {
                                Text(
                                    if (selectedPlaylistIds.size == playlistState.entries.size) "Снять все" else "Все"
                                )
                            }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                        ) {
                            items(playlistState.entries, key = { it.id }) { item ->
                                val isSelected = item.id in selectedPlaylistIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPlaylistIds = if (isSelected) {
                                                selectedPlaylistIds - item.id
                                            } else {
                                                selectedPlaylistIds + item.id
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedPlaylistIds = if (checked) {
                                                selectedPlaylistIds + item.id
                                            } else {
                                                selectedPlaylistIds - item.id
                                            }
                                        },
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            item.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val meta = listOfNotNull(
                                            item.artist.takeIf { it.isNotBlank() },
                                            formatDurationSeconds(item.durationSeconds).takeIf { it.isNotBlank() },
                                        ).joinToString(" • ")
                                        if (meta.isNotBlank()) {
                                            Text(
                                                meta,
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Отмена")
                }

                Button(
                    onClick = {
                        val multiVideo = selectedVideo.size > 1
                        val targets = if (playlistState is PlaylistLoadState.Loaded && selectedPlaylistIds.isNotEmpty()) {
                            playlistState.entries
                                .filter { it.id in selectedPlaylistIds }
                                .map { entry ->
                                    Triple(entry.url, entry.title, entry.durationSeconds) to (entry.artist to entry.thumbnailUrl)
                                }
                        } else {
                            urls.map { u ->
                                val meta = loadedPreview?.takeIf { it.url == u }
                                Triple(u, meta?.title, meta?.durationSeconds ?: 0) to ((meta?.artist.orEmpty()) to meta?.thumbnailUrl)
                            }
                        }

                        val specs = buildList {
                            targets.forEach { (core, extra) ->
                                val (url, title, dur) = core
                                val (artist, thumb) = extra
                                chosenPresets.forEach { preset ->
                                    val audio = if (selectedAudio == PICKER_AUDIO_FROM_SETTINGS) {
                                        if (preset.isAudioOnly) settings.musicAudioCode else settings.videoAudioCode
                                    } else {
                                        selectedAudio
                                    }
                                    add(
                                        PendingDownloadSpec(
                                            url = url,
                                            preset = preset,
                                            audioCode = audio,
                                            title = title,
                                            artist = artist,
                                            thumbnailUrl = thumb,
                                            durationSeconds = dur,
                                            downloadSubtitles = downloadSubs && !preset.isAudioOnly,
                                            formatSuffix = if (!preset.isAudioOnly && multiVideo) preset.id else null,
                                        )
                                    )
                                }
                            }
                        }

                        onConfirm(specs)
                    },
                    enabled = !isProbing && chosenPresets.isNotEmpty() && totalTasks > 0,
                    modifier = Modifier.weight(1.6f),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Скачать ($totalTasks)")
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
