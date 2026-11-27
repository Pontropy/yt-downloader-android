package com.pontropy.ytplayer.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pontropy.ytplayer.data.AppSettings
import com.pontropy.ytplayer.data.AppThemeMode
import com.pontropy.ytplayer.data.AudioLanguages
import kotlinx.coroutines.launch

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTabScreen(
    settings: AppSettings,
    onVideoAudioSelected: (String) -> Unit,
    onMusicAudioSelected: (String) -> Unit,
    onConcurrencySelected: (Int) -> Unit,
    onSubtitlesToggle: (Boolean) -> Unit,
    onPickVideoFolder: () -> Unit,
    onResetVideoFolder: () -> Unit,
    onPickMusicFolder: () -> Unit,
    onResetMusicFolder: () -> Unit,
    onThemeSelected: (AppThemeMode) -> Unit,
    onExportToggle: (Boolean) -> Unit,
    onPickCookiesFile: () -> Unit,
    onClearCookiesFile: () -> Unit,
    onUpdateYtDlp: suspend () -> Result<String>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isUpdating by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Video Settings
        item {
            SectionCard(title = "Видео (YouTube)") {
                Text(
                    "Язык аудиодорожки для видео",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AudioLanguages.commonCodes.forEach { code ->
                        FilterChip(
                            selected = settings.videoAudioCode == code,
                            onClick = { onVideoAudioSelected(code) },
                            label = { Text(AudioLanguages.formatLabel(code)) },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Автозагрузка субтитров (.srt)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = settings.downloadSubtitles, onCheckedChange = onSubtitlesToggle)
                }

                Spacer(Modifier.height(8.dp))
                FolderPickerRow(
                    label = if (settings.videoTreeUri != null) "Папка видео: выбрана своя папка" else "Папка видео: Movies/YT Downloader",
                    hasCustom = settings.videoTreeUri != null,
                    onPick = onPickVideoFolder,
                    onReset = onResetVideoFolder,
                )
            }
        }

        // 2. YouTube Music Settings
        item {
            SectionCard(title = "Музыка (YouTube Music)") {
                Text(
                    "Дорожка для музыки",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AudioLanguages.commonCodes.forEach { code ->
                        FilterChip(
                            selected = settings.musicAudioCode == code,
                            onClick = { onMusicAudioSelected(code) },
                            label = { Text(AudioLanguages.formatLabel(code)) },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                FolderPickerRow(
                    label = if (settings.musicTreeUri != null) "Папка музыки: выбрана своя папка" else "Папка музыки: Music/YT Downloader",
                    hasCustom = settings.musicTreeUri != null,
                    onPick = onPickMusicFolder,
                    onReset = onResetMusicFolder,
                )
            }
        }

        // 3. Queue & Concurrency
        item {
            SectionCard(title = "Очередь и скачивание") {
                Text(
                    "Доступные форматы видео и музыки определяются автоматически для каждой ссылки перед загрузкой.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    "Одновременных загрузок: ${settings.maxConcurrentDownloads}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3).forEach { count ->
                        FilterChip(
                            selected = settings.maxConcurrentDownloads == count,
                            onClick = { onConcurrencySelected(count) },
                            label = { Text("$count поток${if (count > 1) "а" else ""}") },
                        )
                    }
                }
            }
        }

        // 4. Themes Selector
        item {
            SectionCard(title = "Тема оформления") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(AppThemeMode.entries) { mode ->
                        ThemePreviewCard(
                            mode = mode,
                            selected = settings.themeMode == mode,
                            onClick = { onThemeSelected(mode) },
                        )
                    }
                }
            }
        }

        // 5. Storage & yt-dlp Update
        item {
            SectionCard(title = "Хранение и движок yt-dlp") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Копировать в Галерею / Музыку",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Movies/YT Downloader и Music/YT Downloader (если не выбрана своя папка)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = settings.exportToPublicDownloads,
                        onCheckedChange = onExportToggle,
                    )
                }

                Spacer(Modifier.height(8.dp))

                FolderPickerRow(
                    label = if (settings.hasCookiesFile) "Cookies: файл cookies.txt подключён" else "Cookies: не задан (при проверке YouTube)",
                    hasCustom = settings.hasCookiesFile,
                    onPick = onPickCookiesFile,
                    onReset = onClearCookiesFile,
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (!isUpdating) {
                            isUpdating = true
                            updateMessage = null
                            scope.launch {
                                val res = onUpdateYtDlp()
                                updateMessage = res.getOrElse { "Ошибка обновления: ${it.message}" }
                                isUpdating = false
                            }
                        }
                    },
                    enabled = !isUpdating,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Обновление yt-dlp...")
                    } else {
                        Icon(Icons.Default.SystemUpdateAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Обновить yt-dlp")
                    }
                }

                updateMessage?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderPickerRow(
    label: String,
    hasCustom: Boolean,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onPick) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Выбрать")
        }
        if (hasCustom) {
            TextButton(onClick = onReset) {
                Text("Сброс")
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ThemePreviewCard(
    mode: AppThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val (bg, primary, accent) = when (mode) {
        AppThemeMode.TOKYO_NIGHT -> Triple(Color(0xFF1A1B26), Color(0xFFBB9AF7), Color(0xFFFF9E64))
        AppThemeMode.NOTHING_OS -> Triple(Color(0xFF000000), Color(0xFFD71921), Color(0xFFF4F4F5))
        AppThemeMode.CRIMSON_BLACK -> Triple(Color(0xFF080506), Color(0xFFEF233C), Color(0xFFFF5A6E))
        AppThemeMode.MATERIAL_3_DARK -> Triple(Color(0xFF141218), Color(0xFFD0BCFF), Color(0xFFEFB8C8))
        AppThemeMode.MATERIAL_3_DYNAMIC -> Triple(Color(0xFF1F1B24), Color(0xFFA8C7FA), Color(0xFFC2E7FF))
        AppThemeMode.MATERIAL_3_LIGHT -> Triple(Color(0xFFFEF7FF), Color(0xFF6750A4), Color(0xFF7D5260))
        AppThemeMode.MATRIX -> Triple(Color(0xFF030804), Color(0xFF00E676), Color(0xFF00FFA3))
    }

    OutlinedCard(
        modifier = Modifier
            .width(136.dp)
            .clickable(onClick = onClick),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(bg)
                            .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(primary)
                    )
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                }
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Выбрана",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = mode.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}
