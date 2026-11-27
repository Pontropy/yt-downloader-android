package com.pontropy.ytplayer

import android.app.Application
import androidx.core.content.pm.PackageInfoCompat
import com.pontropy.ytplayer.data.AppSettingsRepository
import com.pontropy.ytplayer.data.DownloadController
import com.pontropy.ytplayer.data.MediaLibraryRepository
import com.pontropy.ytplayer.player.PlayerController
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface EngineInitState {
    data object Initializing : EngineInitState
    data object Ready : EngineInitState
    data class Error(val message: String) : EngineInitState
}

class YTPlayerApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var settingsRepository: AppSettingsRepository
        private set
    lateinit var libraryRepository: MediaLibraryRepository
        private set
    lateinit var downloadController: DownloadController
        private set
    lateinit var playerController: PlayerController
        private set

    private val _engineInitState = MutableStateFlow<EngineInitState>(EngineInitState.Initializing)
    val engineInitState: StateFlow<EngineInitState> = _engineInitState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        settingsRepository = AppSettingsRepository(this)
        libraryRepository = MediaLibraryRepository(this)
        downloadController = DownloadController(
            context = this,
            settingsRepository = settingsRepository,
            libraryRepository = libraryRepository,
            appScope = appScope,
        )
        playerController = PlayerController(this, appScope, libraryRepository)

        appScope.launch(Dispatchers.IO) {
            initializeEngines()
        }
    }

    private fun initializeEngines() {
        try {
            syncBundledYtDlpIfUpdated()
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            _engineInitState.value = EngineInitState.Ready
        } catch (t: Throwable) {
            _engineInitState.value = EngineInitState.Error(
                t.message ?: "Не удалось инициализировать yt-dlp / ffmpeg"
            )
        }
    }

    private fun syncBundledYtDlpIfUpdated() {
        runCatching {
            val prefs = getSharedPreferences("yt_downloader_engine", MODE_PRIVATE)
            val pkgInfo = packageManager.getPackageInfo(packageName, 0)
            val stamp = "${PackageInfoCompat.getLongVersionCode(pkgInfo)}_${pkgInfo.lastUpdateTime}"
            val ytdlpDir = File(File(noBackupFilesDir, "youtubedl-android"), "yt-dlp")
            val ytdlpBin = File(ytdlpDir, "yt-dlp")
            val prevStamp = prefs.getString("bundled_ytdlp_stamp", null)
            if (prevStamp != stamp || !ytdlpBin.exists() || ytdlpBin.length() < 100_000L) {
                ytdlpDir.mkdirs()
                val tmp = File(ytdlpDir, "yt-dlp.tmp")
                resources.openRawResource(R.raw.ytdlp).use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (ytdlpBin.exists()) ytdlpBin.delete()
                tmp.renameTo(ytdlpBin)
                ytdlpBin.setExecutable(true)
                prefs.edit().putString("bundled_ytdlp_stamp", stamp).apply()
            }
        }
    }

    suspend fun ensureEngineReady(): Boolean {
        if (_engineInitState.value is EngineInitState.Ready) return true
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            if (_engineInitState.value !is EngineInitState.Ready) {
                initializeEngines()
            }
            _engineInitState.value is EngineInitState.Ready
        }
    }
}
