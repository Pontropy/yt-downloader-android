package com.pontropy.ytplayer.ui.library

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.pontropy.ytplayer.data.MediaEntry
import com.pontropy.ytplayer.data.UserPlaylist
import com.pontropy.ytplayer.ui.formatDurationSeconds
import com.pontropy.ytplayer.ui.formatFileSize

private enum class LibraryFilter(val label: String) {
    ALL("Все"),
    VIDEO("Видео"),
    AUDIO("Музыка"),
    FAVORITES("Избранное"),
    PLAYLISTS("Плейлисты"),
}

private enum class LibrarySort(val label: String) {
    NEWEST("Сначала новые"),
    OLDEST("Сначала старые"),
    TITLE_ASC("По названию А–Я"),
    SIZE_DESC("По размеру"),
}

@Composable
fun LibraryTabScreen(
    entries: List<MediaEntry>,
    playlists: List<UserPlaylist>,
    activeMediaId: String?,
    onPlayFromList: (List<MediaEntry>, Int) -> Unit,
    onPlayNext: (MediaEntry) -> Unit,
    onToggleFavorite: (MediaEntry) -> Unit,
    onShareEntry: (MediaEntry) -> Unit,
    onDeleteEntry: (MediaEntry) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onAddEntryToPlaylist: (playlistId: String, entryId: String) -> Unit,
    onRemoveEntryFromPlaylist: (playlistId: String, entryId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(LibrarySort.NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }

    var activePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var addToPlaylistTarget by remember { mutableStateOf<MediaEntry?>(null) }

    val entryMap = remember(entries) { entries.associateBy { it.id } }

    val activePlaylist = playlists.firstOrNull { it.id == activePlaylistId }

    val filtered = remember(entries, selectedFilter, searchQuery, sortMode) {
        val base = when (selectedFilter) {
            LibraryFilter.ALL -> entries
            LibraryFilter.VIDEO -> entries.filter { !it.isAudioOnly }
            LibraryFilter.AUDIO -> entries.filter { it.isAudioOnly }
            LibraryFilter.FAVORITES -> entries.filter { it.isFavorite }
            LibraryFilter.PLAYLISTS -> emptyList()
        }
        val searched = if (searchQuery.isBlank()) {
            base
        } else {
            val q = searchQuery.trim()
            base.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true) ||
                    it.extension.contains(q, ignoreCase = true)
            }
        }
        when (sortMode) {
            LibrarySort.NEWEST -> searched.sortedByDescending { it.createdAtMillis }
            LibrarySort.OLDEST -> searched.sortedBy { it.createdAtMillis }
            LibrarySort.TITLE_ASC -> searched.sortedBy { it.title.lowercase() }
            LibrarySort.SIZE_DESC -> searched.sortedByDescending { it.fileSizeBytes }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Filter chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LibraryFilter.entries) { filter ->
                val count = when (filter) {
                    LibraryFilter.ALL -> entries.size
                    LibraryFilter.VIDEO -> entries.count { !it.isAudioOnly }
                    LibraryFilter.AUDIO -> entries.count { it.isAudioOnly }
                    LibraryFilter.FAVORITES -> entries.count { it.isFavorite }
                    LibraryFilter.PLAYLISTS -> playlists.size
                }
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = {
                        selectedFilter = filter
                        if (filter != LibraryFilter.PLAYLISTS) activePlaylistId = null
                    },
                    label = { Text("${filter.label} ($count)") },
                )
            }
        }

        if (selectedFilter == LibraryFilter.PLAYLISTS) {
            if (activePlaylist != null) {
                val plEntries = activePlaylist.entryIds.mapNotNull { entryMap[it] }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { activePlaylistId = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                    Text(
                        text = "${activePlaylist.name} (${plEntries.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (plEntries.isNotEmpty()) {
                        TextButton(onClick = { onPlayFromList(plEntries, 0) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("Играть всё")
                        }
                    }
                }

                if (plEntries.isEmpty()) {
                    EmptyMessage(
                        "В плейлисте пока пусто",
                        "Добавляйте треки и видео через меню (⋮) любой карточки в медиатеке.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(plEntries, key = { _, it -> "${activePlaylist.id}_${it.id}" }) { index, entry ->
                            MediaEntryCard(
                                entry = entry,
                                isPlayingCurrent = entry.id == activeMediaId,
                                onClick = { onPlayFromList(plEntries, index) },
                                onPlayNext = { onPlayNext(entry) },
                                onToggleFavorite = { onToggleFavorite(entry) },
                                onAddToPlaylist = { addToPlaylistTarget = entry },
                                onShare = { onShareEntry(entry) },
                                onDelete = { onRemoveEntryFromPlaylist(activePlaylist.id, entry.id) },
                                deleteLabel = "Убрать из плейлиста",
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Локальные плейлисты",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Button(onClick = { showCreatePlaylistDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Создать")
                    }
                }

                if (playlists.isEmpty()) {
                    EmptyMessage(
                        "Плейлистов пока нет",
                        "Создайте плейлист, чтобы собирать музыку или подборки видео для офлайн-плеера.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(playlists, key = { it.id }) { pl ->
                            val plEntries = pl.entryIds.mapNotNull { entryMap[it] }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activePlaylistId = pl.id },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        Icons.Default.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            pl.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            "Элементов: ${plEntries.size}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (plEntries.isNotEmpty()) {
                                        IconButton(onClick = { onPlayFromList(plEntries, 0) }) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Воспроизвести",
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    IconButton(onClick = { onDeletePlaylist(pl.id) }) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Удалить плейлист",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Search & Sort row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Поиск по названию, автору...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Очистить")
                            }
                        }
                    },
                )

                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Сортировка")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                    ) {
                        LibrarySort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        option.label,
                                        fontWeight = if (option == sortMode) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                onClick = {
                                    sortMode = option
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                EmptyMessage(
                    if (searchQuery.isNotBlank()) "Ничего не найдено" else "Медиатека пуста",
                    "Скачанные видео (.mp4) и музыка (.mp3 / .flac / .m4a / .opus) появятся здесь для офлайн-просмотра.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(filtered, key = { _, item -> item.id }) { index, entry ->
                        val isCurrent = entry.id == activeMediaId
                        MediaEntryCard(
                            entry = entry,
                            isPlayingCurrent = isCurrent,
                            onClick = { onPlayFromList(filtered, index) },
                            onPlayNext = { onPlayNext(entry) },
                            onToggleFavorite = { onToggleFavorite(entry) },
                            onAddToPlaylist = { addToPlaylistTarget = entry },
                            onShare = { onShareEntry(entry) },
                            onDelete = { onDeleteEntry(entry) },
                            deleteLabel = "Удалить",
                        )
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Новый плейлист") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Название") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreatePlaylist(newPlaylistName)
                        newPlaylistName = ""
                        showCreatePlaylistDialog = false
                    },
                    enabled = newPlaylistName.trim().isNotEmpty(),
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    addToPlaylistTarget?.let { targetEntry ->
        AlertDialog(
            onDismissRequest = { addToPlaylistTarget = null },
            title = { Text("Добавить в плейлист") },
            text = {
                if (playlists.isEmpty()) {
                    Text("Сначала создайте плейлист во вкладке «Плейлисты».")
                } else {
                    Column {
                        playlists.forEach { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAddEntryToPlaylist(pl.id, targetEntry.id)
                                        addToPlaylistTarget = null
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                                Text(pl.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (playlists.isEmpty()) {
                    TextButton(
                        onClick = {
                            addToPlaylistTarget = null
                            showCreatePlaylistDialog = true
                        }
                    ) {
                        Text("Создать плейлист")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { addToPlaylistTarget = null }) {
                    Text("Закрыть")
                }
            },
        )
    }
}

@Composable
private fun EmptyMessage(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MediaEntryCard(
    entry: MediaEntry,
    isPlayingCurrent: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    deleteLabel: String,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlayingCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 56.dp)
                    .clip(RoundedCornerShape(10.dp))
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
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (isPlayingCurrent) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Equalizer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = entry.extension,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    val details = buildList {
                        if (entry.durationSeconds > 0) add(formatDurationSeconds(entry.durationSeconds))
                        add(formatFileSize(entry.fileSizeBytes))
                    }.joinToString(" • ")
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (entry.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Избранное",
                        tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Меню",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Играть следующим") },
                            leadingIcon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onPlayNext()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("В плейлист...") },
                            leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onAddToPlaylist()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Поделиться") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onShare()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(deleteLabel, color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}
