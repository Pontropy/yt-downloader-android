package com.pontropy.ytplayer

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.pontropy.ytplayer.data.AppSettings
import com.pontropy.ytplayer.data.MediaEntry
import com.pontropy.ytplayer.data.PendingDownloadSpec
import com.pontropy.ytplayer.data.PreviewState
import com.pontropy.ytplayer.data.UrlValidator
import com.pontropy.ytplayer.ui.download.DownloadPickerSheet
import com.pontropy.ytplayer.ui.download.DownloadTabScreen
import com.pontropy.ytplayer.ui.library.LibraryTabScreen
import com.pontropy.ytplayer.ui.player.FullPlayerOverlay
import com.pontropy.ytplayer.ui.player.MiniPlayerBar
import com.pontropy.ytplayer.ui.settings.SettingsTabScreen
import com.pontropy.ytplayer.ui.theme.YTPlayerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private enum class AppTab(val title: String, val icon: ImageVector) {
    DOWNLOAD("Загрузка", Icons.Default.Download),
    LIBRARY("Медиатека", Icons.Default.VideoLibrary),
    SETTINGS("Настройки", Icons.Default.Settings),
}

class MainActivity : ComponentActivity() {

    private var pendingSharedUrl by mutableStateOf<String?>(null)
    private var isInPipState by mutableStateOf(false)
    private var activeVideoInFullPlayer = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Notification permission handled cleanly by Foreground services if granted. */ }

    private val pickVideoTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val app = application as YTPlayerApplication
            lifecycleScope.launch { app.settingsRepository.setVideoTreeUri(uri.toString()) }
        }
    }

    private val pickMusicTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val app = application as YTPlayerApplication
            lifecycleScope.launch { app.settingsRepository.setMusicTreeUri(uri.toString()) }
        }
    }

    private val pickCookiesFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val app = application as YTPlayerApplication
            lifecycleScope.launch {
                val ok = runCatching {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        if (bytes.isEmpty()) error("Пустой файл")
                        File(filesDir, "cookies.txt").writeBytes(bytes)
                    } ?: error("Не удалось открыть файл")
                }.isSuccess
                if (ok) {
                    app.settingsRepository.setHasCookiesFile(true)
                }
            }
        }
    }

    @kotlin.OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyPreferredHighRefreshRate()
        handleIncomingIntent(intent)
        requestNotificationsIfNeeded()

        addOnPictureInPictureModeChangedListener { info ->
            isInPipState = info.isInPictureInPictureMode
        }

        val app = application as YTPlayerApplication

        setContent {
            val settings by app.settingsRepository.settingsFlow.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )
            val engineState by app.engineInitState.collectAsStateWithLifecycle()
            val previewState by app.downloadController.preview.collectAsStateWithLifecycle()
            val playlistState by app.downloadController.playlistState.collectAsStateWithLifecycle()
            val progressState by app.downloadController.progress.collectAsStateWithLifecycle()
            val queue by app.downloadController.queue.collectAsStateWithLifecycle()
            val libraryEntries by app.libraryRepository.entries.collectAsStateWithLifecycle()
            val userPlaylists by app.libraryRepository.playlists.collectAsStateWithLifecycle()
            val playerState by app.playerController.state.collectAsStateWithLifecycle()
            val playerInstance by app.playerController.playerInstance.collectAsStateWithLifecycle()

            var currentTab by rememberSaveable { mutableStateOf(AppTab.DOWNLOAD) }
            var urlInput by rememberSaveable { mutableStateOf("") }
            var isFullPlayerOpen by rememberSaveable { mutableStateOf(false) }
            var pickerUrls by remember { mutableStateOf<List<String>?>(null) }

            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            activeVideoInFullPlayer = isFullPlayerOpen &&
                playerState.currentEntry != null &&
                !playerState.currentEntry!!.isAudioOnly &&
                playerState.isPlaying

            // Handle shared URLs coming from YouTube / YouTube Music app
            LaunchedEffect(pendingSharedUrl) {
                val shared = pendingSharedUrl ?: return@LaunchedEffect
                urlInput = shared
                currentTab = AppTab.DOWNLOAD
                isFullPlayerOpen = false
                pendingSharedUrl = null
                app.downloadController.fetchPreview(shared) { app.ensureEngineReady() }
                app.downloadController.clearPlaylistState()
                pickerUrls = listOf(shared)
            }

            // Debounced metadata preview on typing
            LaunchedEffect(urlInput) {
                val urls = UrlValidator.parseUrls(urlInput)
                if (urls.size == 1) {
                    delay(500L)
                    app.downloadController.fetchPreview(urls.first()) { app.ensureEngineReady() }
                } else {
                    app.downloadController.clearPreview()
                }
            }

            YTPlayerTheme(themeMode = settings.themeMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (!isInPipState) {
                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Text(
                                            text = when (currentTab) {
                                                AppTab.DOWNLOAD -> "YT Downloader"
                                                AppTab.LIBRARY -> "Медиатека"
                                                AppTab.SETTINGS -> "Настройки"
                                            },
                                            fontWeight = FontWeight.Bold,
                                        )
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        titleContentColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            },
                            bottomBar = {
                                Column {
                                    MiniPlayerBar(
                                        state = playerState,
                                        positionMsFlow = app.playerController.positionMsFlow,
                                        onExpand = { isFullPlayerOpen = true },
                                        onTogglePlayPause = { app.playerController.togglePlayPause() },
                                        onNext = { app.playerController.skipNext() },
                                        onClose = {
                                            isFullPlayerOpen = false
                                            app.playerController.stopAndClear()
                                        },
                                    )
                                    NavigationBar {
                                        AppTab.entries.forEach { tab ->
                                            NavigationBarItem(
                                                selected = currentTab == tab,
                                                onClick = { currentTab = tab },
                                                icon = { Icon(tab.icon, contentDescription = tab.title) },
                                                label = { Text(tab.title) },
                                            )
                                        }
                                    }
                                }
                            },
                        ) { innerPadding ->
                            when (currentTab) {
                                AppTab.DOWNLOAD -> {
                                    DownloadTabScreen(
                                        urlText = urlInput,
                                        onUrlChange = { urlInput = it },
                                        onPasteClick = {
                                            val clip = readClipboardText(this@MainActivity)
                                            if (clip.isNullOrBlank()) {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Буфер обмена пуст")
                                                }
                                            } else {
                                                urlInput = clip.trim()
                                            }
                                        },
                                        onDownloadClick = {
                                            val urls = UrlValidator.parseUrls(urlInput)
                                            if (urls.isEmpty()) {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Введите корректную ссылку https://...")
                                                }
                                            } else {
                                                app.downloadController.clearPlaylistState()
                                                if (urls.size == 1) {
                                                    app.downloadController.fetchPreview(urls.first()) { app.ensureEngineReady() }
                                                }
                                                pickerUrls = urls
                                            }
                                        },
                                        onCancelClick = { app.downloadController.cancelAllActive() },
                                        onRemoveQueueItem = { id -> app.downloadController.removeItem(id) },
                                        onClearQueue = { app.downloadController.clearFinished() },
                                        onOpenSettings = { currentTab = AppTab.SETTINGS },
                                        settings = settings,
                                        engineState = engineState,
                                        previewState = previewState,
                                        progressState = progressState,
                                        queue = queue,
                                        modifier = Modifier.padding(innerPadding),
                                    )
                                }

                                AppTab.LIBRARY -> {
                                    LibraryTabScreen(
                                        entries = libraryEntries,
                                        playlists = userPlaylists,
                                        activeMediaId = playerState.currentEntry?.id,
                                        onPlayFromList = { list, idx ->
                                            app.playerController.playPlaylist(list, idx)
                                            isFullPlayerOpen = true
                                        },
                                        onPlayNext = { entry ->
                                            app.playerController.playNext(entry)
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Добавлено следующим: ${entry.title}")
                                            }
                                        },
                                        onToggleFavorite = { entry ->
                                            scope.launch { app.libraryRepository.toggleFavorite(entry.id) }
                                        },
                                        onShareEntry = { entry -> shareMediaEntry(app, entry) },
                                        onDeleteEntry = { entry ->
                                            if (playerState.currentEntry?.id == entry.id) {
                                                isFullPlayerOpen = false
                                                app.playerController.stopAndClear()
                                            }
                                            scope.launch {
                                                app.libraryRepository.deleteEntry(entry.id)
                                            }
                                        },
                                        onCreatePlaylist = { name ->
                                            scope.launch { app.libraryRepository.createPlaylist(name) }
                                        },
                                        onDeletePlaylist = { plId ->
                                            scope.launch { app.libraryRepository.deletePlaylist(plId) }
                                        },
                                        onAddEntryToPlaylist = { plId, entryId ->
                                            scope.launch {
                                                app.libraryRepository.addEntryToPlaylist(plId, entryId)
                                                snackbarHostState.showSnackbar("Добавлено в плейлист")
                                            }
                                        },
                                        onRemoveEntryFromPlaylist = { plId, entryId ->
                                            scope.launch {
                                                app.libraryRepository.removeEntryFromPlaylist(plId, entryId)
                                            }
                                        },
                                        modifier = Modifier.padding(innerPadding),
                                    )
                                }

                                AppTab.SETTINGS -> {
                                    SettingsTabScreen(
                                        settings = settings,
                                        onVideoAudioSelected = { code ->
                                            scope.launch { app.settingsRepository.setVideoAudioCode(code) }
                                        },
                                        onMusicAudioSelected = { code ->
                                            scope.launch { app.settingsRepository.setMusicAudioCode(code) }
                                        },
                                        onConcurrencySelected = { limit ->
                                            scope.launch { app.settingsRepository.setMaxConcurrentDownloads(limit) }
                                        },
                                        onSubtitlesToggle = { enabled ->
                                            scope.launch { app.settingsRepository.setDownloadSubtitles(enabled) }
                                        },
                                        onPickVideoFolder = { pickVideoTreeLauncher.launch(null) },
                                        onResetVideoFolder = {
                                            scope.launch { app.settingsRepository.setVideoTreeUri(null) }
                                        },
                                        onPickMusicFolder = { pickMusicTreeLauncher.launch(null) },
                                        onResetMusicFolder = {
                                            scope.launch { app.settingsRepository.setMusicTreeUri(null) }
                                        },
                                        onThemeSelected = { t ->
                                            scope.launch { app.settingsRepository.setThemeMode(t) }
                                        },
                                        onExportToggle = { enabled ->
                                            scope.launch {
                                                app.settingsRepository.setExportToPublicDownloads(enabled)
                                            }
                                        },
                                        onPickCookiesFile = {
                                            pickCookiesFileLauncher.launch(arrayOf("text/*", "*/*"))
                                        },
                                        onClearCookiesFile = {
                                            File(filesDir, "cookies.txt").delete()
                                            scope.launch { app.settingsRepository.setHasCookiesFile(false) }
                                        },
                                        onUpdateYtDlp = { app.downloadController.updateYtDlp() },
                                        modifier = Modifier.padding(innerPadding),
                                    )
                                }
                            }
                        }
                    }

                    pickerUrls?.let { urlsToPick ->
                        DownloadPickerSheet(
                            urls = urlsToPick,
                            settings = settings,
                            previewState = previewState,
                            playlistState = playlistState,
                            onLoadPlaylist = { plUrl ->
                                app.downloadController.loadPlaylistEntries(plUrl) { app.ensureEngineReady() }
                            },
                            onDismiss = {
                                pickerUrls = null
                                app.downloadController.clearPlaylistState()
                            },
                            onConfirm = { specs ->
                                pickerUrls = null
                                app.downloadController.clearPlaylistState()
                                app.downloadController.enqueueSpecs(specs) { app.ensureEngineReady() }
                                urlInput = ""
                                app.downloadController.clearPreview()
                            },
                        )
                    }

                    if (isFullPlayerOpen && playerState.currentEntry != null) {
                        FullPlayerOverlay(
                            state = playerState,
                            positionMsFlow = app.playerController.positionMsFlow,
                            playerInstance = playerInstance,
                            controller = app.playerController,
                            isInPictureInPicture = isInPipState,
                            onEnterPip = { enterVideoPipMode() },
                            onCollapse = { isFullPlayerOpen = false },
                        )
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyPreferredHighRefreshRate()
        }
    }

    @Suppress("DEPRECATION")
    private fun applyPreferredHighRefreshRate() {
        val currentDisplay: Display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display ?: windowManager.defaultDisplay
        } else {
            windowManager.defaultDisplay
        } ?: return

        val activeMode = currentDisplay.mode ?: return
        val bestMode = currentDisplay.supportedModes
            .filter {
                it.physicalWidth == activeMode.physicalWidth &&
                    it.physicalHeight == activeMode.physicalHeight
            }
            .maxByOrNull { it.refreshRate }
            ?: activeMode

        val attrs = window.attributes
        if (attrs.preferredDisplayModeId != bestMode.modeId || attrs.preferredRefreshRate != bestMode.refreshRate) {
            attrs.preferredDisplayModeId = bestMode.modeId
            attrs.preferredRefreshRate = bestMode.refreshRate
            window.attributes = attrs
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (activeVideoInFullPlayer) {
            enterVideoPipMode()
        }
    }

    private fun enterVideoPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            runCatching {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val extracted = UrlValidator.parseUrls(text).firstOrNull() ?: return
        pendingSharedUrl = extracted
    }

    private fun readClipboardText(context: Context): String? {
        val manager = context.getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }

    private fun shareMediaEntry(app: YTPlayerApplication, entry: MediaEntry) {
        val uri = app.libraryRepository.getShareUri(entry)
        val mime = if (entry.isAudioOnly) "audio/*" else "video/*"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, entry.title))
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
