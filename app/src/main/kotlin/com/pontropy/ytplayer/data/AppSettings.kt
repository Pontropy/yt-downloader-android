package com.pontropy.ytplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "yt_player_settings"
)

data class AppSettings(
    val videoAudioCode: String = AudioLanguages.ORIGINAL,
    val musicAudioCode: String = AudioLanguages.ORIGINAL,
    val maxConcurrentDownloads: Int = 2,
    val downloadSubtitles: Boolean = false,
    val videoTreeUri: String? = null,
    val musicTreeUri: String? = null,
    val themeMode: AppThemeMode = AppThemeMode.TOKYO_NIGHT,
    val exportToPublicDownloads: Boolean = true,
    val hasCookiesFile: Boolean = false,
) {
    fun audioCodeFor(preset: QualityPreset): String =
        if (preset.isAudioOnly) musicAudioCode else videoAudioCode
}

class AppSettingsRepository(private val context: Context) {

    private object Keys {
        val LEGACY_AUDIO = stringPreferencesKey("audio_track")
        val VIDEO_AUDIO = stringPreferencesKey("video_audio_code")
        val MUSIC_AUDIO = stringPreferencesKey("music_audio_code")
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent_downloads")
        val DOWNLOAD_SUBS = booleanPreferencesKey("download_subtitles")
        val VIDEO_TREE_URI = stringPreferencesKey("video_tree_uri")
        val MUSIC_TREE_URI = stringPreferencesKey("music_tree_uri")
        val THEME = stringPreferencesKey("theme_mode")
        val EXPORT_PUBLIC = booleanPreferencesKey("export_to_public_downloads")
        val HAS_COOKIES_FILE = booleanPreferencesKey("has_cookies_file")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val legacyAudio = AudioLanguages.normalizeCode(prefs[Keys.LEGACY_AUDIO])
        val videoAudio = AudioLanguages.normalizeCode(prefs[Keys.VIDEO_AUDIO] ?: legacyAudio)
        val musicAudio = AudioLanguages.normalizeCode(prefs[Keys.MUSIC_AUDIO] ?: legacyAudio)
        val conc = (prefs[Keys.MAX_CONCURRENT] ?: 2).coerceIn(1, 3)

        AppSettings(
            videoAudioCode = videoAudio,
            musicAudioCode = musicAudio,
            maxConcurrentDownloads = conc,
            downloadSubtitles = prefs[Keys.DOWNLOAD_SUBS] ?: false,
            videoTreeUri = prefs[Keys.VIDEO_TREE_URI]?.takeIf { it.isNotBlank() },
            musicTreeUri = prefs[Keys.MUSIC_TREE_URI]?.takeIf { it.isNotBlank() },
            themeMode = AppThemeMode.fromId(prefs[Keys.THEME]),
            exportToPublicDownloads = prefs[Keys.EXPORT_PUBLIC] ?: true,
            hasCookiesFile = prefs[Keys.HAS_COOKIES_FILE] ?: false,
        )
    }

    suspend fun setVideoAudioCode(code: String) {
        context.settingsDataStore.edit { it[Keys.VIDEO_AUDIO] = AudioLanguages.normalizeCode(code) }
    }

    suspend fun setMusicAudioCode(code: String) {
        context.settingsDataStore.edit { it[Keys.MUSIC_AUDIO] = AudioLanguages.normalizeCode(code) }
    }

    suspend fun setMaxConcurrentDownloads(limit: Int) {
        context.settingsDataStore.edit { it[Keys.MAX_CONCURRENT] = limit.coerceIn(1, 3) }
    }

    suspend fun setDownloadSubtitles(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DOWNLOAD_SUBS] = enabled }
    }

    suspend fun setVideoTreeUri(uri: String?) {
        context.settingsDataStore.edit {
            if (uri.isNullOrBlank()) it.remove(Keys.VIDEO_TREE_URI) else it[Keys.VIDEO_TREE_URI] = uri
        }
    }

    suspend fun setMusicTreeUri(uri: String?) {
        context.settingsDataStore.edit {
            if (uri.isNullOrBlank()) it.remove(Keys.MUSIC_TREE_URI) else it[Keys.MUSIC_TREE_URI] = uri
        }
    }

    suspend fun setThemeMode(theme: AppThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME] = theme.id }
    }

    suspend fun setExportToPublicDownloads(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.EXPORT_PUBLIC] = enabled }
    }

    suspend fun setHasCookiesFile(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HAS_COOKIES_FILE] = enabled }
    }
}
