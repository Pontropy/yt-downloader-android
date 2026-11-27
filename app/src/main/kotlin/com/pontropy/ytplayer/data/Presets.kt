package com.pontropy.ytplayer.data

import java.net.URI

enum class QualityPreset(
    val id: String,
    val label: String,
    val shortLabel: String,
    val description: String,
    val isAudioOnly: Boolean,
) {
    VIDEO_360P(
        id = "360p",
        label = "Видео 360p (.mp4)",
        shortLabel = "360p",
        description = "Компактное видео до 360p (.mp4)",
        isAudioOnly = false,
    ),
    VIDEO_720P(
        id = "720p",
        label = "Видео 720p HD (.mp4)",
        shortLabel = "720p",
        description = "HD видео до 720p (.mp4)",
        isAudioOnly = false,
    ),
    VIDEO_1080P(
        id = "1080p",
        label = "Видео 1080p FullHD (.mp4)",
        shortLabel = "1080p",
        description = "FullHD до 1080p (.mp4)",
        isAudioOnly = false,
    ),
    VIDEO_1440P(
        id = "1440p",
        label = "Видео 2K 1440p (.mp4)",
        shortLabel = "2K 1440p",
        description = "2K QHD до 1440p (.mp4)",
        isAudioOnly = false,
    ),
    VIDEO_2160P(
        id = "2160p",
        label = "Видео 4K 2160p (.mp4)",
        shortLabel = "4K 2160p",
        description = "4K UltraHD до 2160p (.mp4)",
        isAudioOnly = false,
    ),
    VIDEO_MAX(
        id = "max",
        label = "Видео максимум (.mp4)",
        shortLabel = "Макс MP4",
        description = "Лучшее доступное видео без ограничения разрешения (.mp4)",
        isAudioOnly = false,
    ),
    AUDIO_MP3(
        id = "audio_mp3",
        label = "Аудио MP3 (YT Music)",
        shortLabel = "MP3",
        description = "Только звук: лучший VBR (~V0), обложка и теги",
        isAudioOnly = true,
    ),
    AUDIO_FLAC(
        id = "audio_flac",
        label = "Аудио FLAC (YT Music)",
        shortLabel = "FLAC",
        description = "Перекодирование из лучшего потока во FLAC + обложка и теги",
        isAudioOnly = true,
    ),
    AUDIO_M4A(
        id = "audio_m4a",
        label = "Аудио M4A / AAC (YT Music)",
        shortLabel = "M4A",
        description = "Лучший поток AAC (.m4a) + обложка и теги",
        isAudioOnly = true,
    ),
    AUDIO_OPUS(
        id = "audio_opus",
        label = "Аудио OPUS (YT Music)",
        shortLabel = "OPUS",
        description = "Максимальное качество OPUS + обложка и теги",
        isAudioOnly = true,
    );

    companion object {
        val videoEntries: List<QualityPreset>
            get() = entries.filterNot { it.isAudioOnly }

        val musicEntries: List<QualityPreset>
            get() = entries.filter { it.isAudioOnly }

        fun fromId(id: String?): QualityPreset =
            entries.firstOrNull { it.id == id } ?: VIDEO_720P

        fun fromIdOrNull(id: String?): QualityPreset? =
            entries.firstOrNull { it.id == id }
    }
}

enum class AudioTrackMode(
    val id: String,
    val label: String,
    val shortLabel: String,
) {
    ORIGINAL(
        id = "original",
        label = "Оригинал (без AI-дубляжа)",
        shortLabel = "Оригинал",
    ),
    RUSSIAN(
        id = "ru",
        label = "Русская озвучка",
        shortLabel = "Русская",
    );

    companion object {
        fun fromId(id: String?): AudioTrackMode =
            entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}

data class AudioTrackOption(
    val code: String,
    val label: String,
    val isOriginal: Boolean = false,
)

object AudioLanguages {
    const val ORIGINAL = "original"
    const val RUSSIAN = "ru"

    private val langRegex = Regex("^[a-z]{2,3}(?:-[a-z0-9]{2,8})?$")

    private val displayNames = mapOf(
        "ru" to "Русский",
        "en" to "English",
        "uk" to "Українська",
        "es" to "Español",
        "de" to "Deutsch",
        "fr" to "Français",
        "ja" to "日本語",
        "ko" to "한국어",
        "zh" to "中文",
        "pt" to "Português",
        "it" to "Italiano",
        "tr" to "Türkçe",
        "pl" to "Polski",
        "ar" to "العربية",
        "hi" to "हिन्दी",
    )

    val commonCodes: List<String> = listOf(ORIGINAL, RUSSIAN, "en", "uk", "es", "de", "fr", "ja")

    fun normalizeCode(raw: String?): String {
        val cleaned = raw?.trim()?.lowercase().orEmpty()
        if (cleaned == ORIGINAL) return ORIGINAL
        if (langRegex.matches(cleaned)) return cleaned
        return ORIGINAL
    }

    fun isValidCodeOrNull(raw: String?): String? {
        val cleaned = raw?.trim()?.lowercase().orEmpty()
        if (cleaned == ORIGINAL) return ORIGINAL
        return if (langRegex.matches(cleaned)) cleaned else null
    }

    fun formatLabel(code: String, note: String = ""): String {
        val norm = normalizeCode(code)
        if (norm == ORIGINAL) return AudioTrackMode.ORIGINAL.label
        val base = norm.substringBefore('-')
        val name = displayNames[norm] ?: displayNames[base] ?: norm.uppercase()
        val low = note.lowercase()
        val qualifier = when {
            "original" in low -> ", оригинал"
            "dub" in low -> ", дубляж"
            else -> ""
        }
        return "$name ($norm$qualifier)"
    }

    fun shortLabel(code: String): String {
        val norm = normalizeCode(code)
        return when (norm) {
            ORIGINAL -> AudioTrackMode.ORIGINAL.shortLabel
            RUSSIAN -> AudioTrackMode.RUSSIAN.shortLabel
            else -> norm.uppercase()
        }
    }
}

enum class AppThemeMode(
    val id: String,
    val label: String,
    val isDark: Boolean,
) {
    TOKYO_NIGHT("tokyo-night", "Tokyo Night", true),
    NOTHING_OS("nothing-os", "Nothing OS", true),
    CRIMSON_BLACK("crimson-black", "Красно-чёрная", true),
    MATERIAL_3_DARK("material-3", "Material Design 3 (Dark)", true),
    MATERIAL_3_DYNAMIC("material-you", "Material You (Динамическая)", true),
    MATERIAL_3_LIGHT("material-3-light", "Material Design 3 (Light)", false),
    MATRIX("matrix-cyber", "Matrix (чёрно-зелёная)", true);

    companion object {
        fun fromId(id: String?): AppThemeMode =
            entries.firstOrNull { it.id == id } ?: TOKYO_NIGHT
    }
}

object PresetFlagBuilder {

    fun buildArgs(quality: QualityPreset, audio: AudioTrackMode): List<Pair<String, String?>> {
        return buildArgs(quality, audio.id, downloadSubtitles = false)
    }

    /**
     * Build the exact yt-dlp CLI flag pairs for quality preset, audio language, and optional subtitles.
     */
    fun buildArgs(
        quality: QualityPreset,
        audioCode: String,
        downloadSubtitles: Boolean = false,
    ): List<Pair<String, String?>> {
        val normAudio = AudioLanguages.normalizeCode(audioCode)
        val audioSelector = if (normAudio == AudioLanguages.ORIGINAL) {
            "(ba[format_note*=original]/ba)"
        } else {
            "(ba[language^=$normAudio]/ba)"
        }

        val baseArgs: List<Pair<String, String?>> = when (quality) {
            QualityPreset.VIDEO_360P -> listOf(
                "-f" to "bv[height<=360][vcodec!*=av01]+$audioSelector/bv[height<=360]+$audioSelector/b[height<=360]",
                "--merge-output-format" to "mp4",
            )

            QualityPreset.VIDEO_720P -> listOf(
                "-f" to "bv[height<=720]+$audioSelector/b[height<=720]",
                "-S" to "vcodec:h264,acodec:aac",
                "--merge-output-format" to "mp4",
            )

            QualityPreset.VIDEO_1080P -> listOf(
                "-f" to "bv[height<=1080][vcodec!*=av01]+$audioSelector/bv[height<=1080]+$audioSelector/b[height<=1080]",
                "--merge-output-format" to "mp4",
            )

            QualityPreset.VIDEO_1440P -> listOf(
                "-f" to "bv[height<=1440][vcodec!*=av01]+$audioSelector/bv[height<=1440]+$audioSelector/b[height<=1440]",
                "--merge-output-format" to "mp4",
            )

            QualityPreset.VIDEO_2160P -> listOf(
                "-f" to "bv[height<=2160][vcodec!*=av01]+$audioSelector/bv[height<=2160]+$audioSelector/b[height<=2160]",
                "--merge-output-format" to "mp4",
            )

            QualityPreset.VIDEO_MAX -> listOf(
                "-f" to "bv*[vcodec!*=av01]+$audioSelector/bv*+$audioSelector/b",
                "--merge-output-format" to "mp4",
            )

            QualityPreset.AUDIO_MP3 -> listOf(
                "-f" to "$audioSelector/b",
                "-x" to null,
                "--audio-format" to "mp3",
                "--audio-quality" to "0",
                "--embed-metadata" to null,
                "--embed-thumbnail" to null,
            )

            QualityPreset.AUDIO_FLAC -> listOf(
                "-f" to "$audioSelector/b",
                "-x" to null,
                "--audio-format" to "flac",
                "--embed-metadata" to null,
                "--embed-thumbnail" to null,
            )

            QualityPreset.AUDIO_M4A -> listOf(
                "-f" to "$audioSelector/b",
                "-x" to null,
                "--audio-format" to "m4a",
                "--audio-quality" to "0",
                "--embed-metadata" to null,
                "--embed-thumbnail" to null,
            )

            QualityPreset.AUDIO_OPUS -> listOf(
                "-f" to "$audioSelector/b",
                "-x" to null,
                "--audio-format" to "opus",
                "--audio-quality" to "0",
                "--embed-metadata" to null,
                "--embed-thumbnail" to null,
            )
        }

        if (!downloadSubtitles || quality.isAudioOnly) {
            return baseArgs
        }

        val subLangs = if (normAudio != AudioLanguages.ORIGINAL && normAudio != AudioLanguages.RUSSIAN) {
            "$normAudio,ru,en"
        } else {
            "ru,en"
        }

        return baseArgs + listOf(
            "--write-subs" to null,
            "--write-auto-subs" to null,
            "--sub-langs" to subLangs,
            "--convert-subs" to "srt",
        )
    }
}

object UrlValidator {
    private val youtubeHosts = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtu.be",
        "www.youtu.be",
    )

    private val urlExtractRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)

    fun isValidHttpUrl(rawUrl: String): Boolean {
        val url = rawUrl.trim()
        if (url.isEmpty() || url.any { it.isWhitespace() }) return false
        return try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    fun isYouTubeUrl(rawUrl: String): Boolean {
        val url = rawUrl.trim()
        if (!isValidHttpUrl(url)) return false
        return try {
            val host = URI(url).host?.lowercase() ?: return false
            host in youtubeHosts
        } catch (_: Exception) {
            false
        }
    }

    fun isPlaylistUrl(rawUrl: String): Boolean {
        val url = rawUrl.trim()
        if (!isValidHttpUrl(url)) return false
        return try {
            val uri = URI(url)
            val query = uri.rawQuery.orEmpty()
            val path = uri.path.orEmpty().lowercase()
            path.contains("/playlist") || query.split("&").any { it.startsWith("list=") && it.length > 5 }
        } catch (_: Exception) {
            false
        }
    }

    fun isPurePlaylistUrl(rawUrl: String): Boolean {
        val url = rawUrl.trim()
        if (!isValidHttpUrl(url)) return false
        return try {
            val uri = URI(url)
            val params = uri.rawQuery.orEmpty().split("&")
            val hasList = params.any { it.startsWith("list=") && it.length > 5 }
            val hasVideo = params.any { it.startsWith("v=") && it.length > 2 }
            val path = uri.path.orEmpty().lowercase()
            path.contains("/playlist") || (hasList && !hasVideo)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Extract one or more URLs from pasted or shared text.
     */
    fun parseUrls(rawText: String): List<String> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()
        val tokens = trimmed.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.isNotEmpty() && tokens.all { isValidHttpUrl(it) }) {
            return tokens
        }
        return urlExtractRegex.findAll(trimmed)
            .map { it.value.trimEnd('.', ',', ';', ')', ']') }
            .filter { isValidHttpUrl(it) }
            .distinct()
            .toList()
    }
}
