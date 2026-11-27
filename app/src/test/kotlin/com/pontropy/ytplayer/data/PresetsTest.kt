package com.pontropy.ytplayer.data

import com.pontropy.ytplayer.ui.formatDurationMillis
import com.pontropy.ytplayer.ui.formatDurationSeconds
import com.pontropy.ytplayer.ui.formatFileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsTest {

    @Test
    fun preset360pAnd720pFlagsMatchExactSpec() {
        val args360 = PresetFlagBuilder.buildArgs(QualityPreset.VIDEO_360P, AudioTrackMode.RUSSIAN)
        assertEquals(
            listOf(
                "-f" to "bv[height<=360][vcodec!*=av01]+(ba[language^=ru]/ba)/bv[height<=360]+(ba[language^=ru]/ba)/b[height<=360]",
                "--merge-output-format" to "mp4",
            ),
            args360,
        )

        val args720 = PresetFlagBuilder.buildArgs(QualityPreset.VIDEO_720P, AudioTrackMode.ORIGINAL)
        assertEquals(
            listOf(
                "-f" to "bv[height<=720]+(ba[format_note*=original]/ba)/b[height<=720]",
                "-S" to "vcodec:h264,acodec:aac",
                "--merge-output-format" to "mp4",
            ),
            args720,
        )
    }

    @Test
    fun presetMaxFlagsMatchExactSpec() {
        assertEquals(
            listOf(
                "-f" to "bv*[vcodec!*=av01]+(ba[language^=ru]/ba)/bv*+(ba[language^=ru]/ba)/b",
                "--merge-output-format" to "mp4",
            ),
            PresetFlagBuilder.buildArgs(QualityPreset.VIDEO_MAX, AudioTrackMode.RUSSIAN),
        )
        assertEquals(
            listOf(
                "-f" to "bv*[vcodec!*=av01]+(ba[format_note*=original]/ba)/bv*+(ba[format_note*=original]/ba)/b",
                "--merge-output-format" to "mp4",
            ),
            PresetFlagBuilder.buildArgs(QualityPreset.VIDEO_MAX, AudioTrackMode.ORIGINAL),
        )
    }

    @Test
    fun presetAudioMp3AndFlacFlags() {
        assertEquals(
            listOf(
                "-f" to "(ba[language^=ru]/ba)/b",
                "-x" to null,
                "--audio-format" to "mp3",
                "--audio-quality" to "0",
                "--embed-metadata" to null,
                "--embed-thumbnail" to null,
            ),
            PresetFlagBuilder.buildArgs(QualityPreset.AUDIO_MP3, AudioTrackMode.RUSSIAN),
        )
        assertEquals(
            listOf(
                "-f" to "(ba[format_note*=original]/ba)/b",
                "-x" to null,
                "--audio-format" to "flac",
                "--embed-metadata" to null,
                "--embed-thumbnail" to null,
            ),
            PresetFlagBuilder.buildArgs(QualityPreset.AUDIO_FLAC, AudioTrackMode.ORIGINAL),
        )
    }

    @Test
    fun extraPresetsDynamicAudioLanguagesAndSubtitles() {
        assertEquals(
            listOf(
                "-f" to "bv[height<=720]+(ba[language^=es]/ba)/b[height<=720]",
                "-S" to "vcodec:h264,acodec:aac",
                "--merge-output-format" to "mp4",
                "--write-subs" to null,
                "--write-auto-subs" to null,
                "--sub-langs" to "es,ru,en",
                "--convert-subs" to "srt",
            ),
            PresetFlagBuilder.buildArgs(QualityPreset.VIDEO_720P, "es", downloadSubtitles = true),
        )
        assertEquals(
            listOf(
                "-f" to "(ba[language^=ja]/ba)/b",
                "-x" to null,
                "--audio-format" to "m4a",
                "--audio-quality" to "0",
                "--embed-metadata" to null,
                "--embed-thumbnail" to null,
            ),
            PresetFlagBuilder.buildArgs(QualityPreset.AUDIO_M4A, "ja"),
        )
        assertEquals(
            listOf(
                "-f" to "bv[height<=1440][vcodec!*=av01]+(ba[language^=ru]/ba)/bv[height<=1440]+(ba[language^=ru]/ba)/b[height<=1440]",
                "--merge-output-format" to "mp4",
            ),
            PresetFlagBuilder.buildArgs(QualityPreset.VIDEO_1440P, "ru"),
        )
        assertEquals(
            listOf(
                "-f" to "bv[height<=2160][vcodec!*=av01]+(ba[format_note*=original]/ba)/bv[height<=2160]+(ba[format_note*=original]/ba)/b[height<=2160]",
                "--merge-output-format" to "mp4",
            ),
            PresetFlagBuilder.buildArgs(QualityPreset.VIDEO_2160P, "original"),
        )
        assertEquals(
            listOf(
                "-f" to "(ba[format_note*=original]/ba)/b",
                "-x" to null,
                "--audio-format" to "opus",
                "--audio-quality" to "0",
                "--embed-metadata" to null,
                "--embed-thumbnail" to null,
            ),
            PresetFlagBuilder.buildArgs(QualityPreset.AUDIO_OPUS, "original"),
        )
    }

    @Test
    fun metadataParserExtractsAudioLanguagesAndPlaylists() {
        val raw = """
            {
              "title": "Multilingual Video",
              "uploader": "Channel",
              "duration": 150,
              "webpage_url": "https://www.youtube.com/watch?v=xyz&list=PL123",
              "formats": [
                {"vcodec": "avc1", "acodec": "none", "height": 1080},
                {"vcodec": "none", "acodec": "mp4a", "language": "ru", "format_note": "Russian dubbed"},
                {"vcodec": "none", "acodec": "mp4a", "language": "en", "format_note": "English original"},
                {"vcodec": "none", "acodec": "mp4a", "language": "de", "format_note": "German dubbed"}
              ]
            }
        """.trimIndent()
        val parsed = MetadataJsonParser.parseSingleVideo(
            raw,
            "https://www.youtube.com/watch?v=xyz&list=PL123",
        )
        assertNotNull(parsed)
        assertEquals(listOf("original", "en", "ru", "de"), parsed!!.availableAudioTracks.map { it.code })
        assertTrue(parsed.isPlaylist)
        assertTrue(parsed.hasRussianAudio)
        assertEquals(1080, parsed.maxHeight)

        val plRaw = """
            {
              "title": "Test Mix",
              "entries": [
                {"id": "aaa111", "title": "Track One", "uploader": "Artist A", "duration": 95},
                {"id": "dead00", "title": "[Deleted video]"},
                {"id": "bbb222", "title": "Track Two", "duration": 200}
              ]
            }
        """.trimIndent()
        val pl = MetadataJsonParser.parsePlaylist(plRaw)
        assertNotNull(pl)
        assertEquals("Test Mix", pl!!.first)
        assertEquals(2, pl.second.size)
        assertEquals("https://www.youtube.com/watch?v=aaa111", pl.second[0].url)

        // Check per-video & per-music format availability probing
        assertTrue(parsed.formatsProbed)
        assertEquals(
            listOf(
                QualityPreset.VIDEO_360P,
                QualityPreset.VIDEO_720P,
                QualityPreset.VIDEO_1080P,
                QualityPreset.VIDEO_MAX,
            ),
            parsed.availableVideoPresets,
        )
        assertEquals(QualityPreset.musicEntries, parsed.availableMusicPresets)

        // Pure audio / YT Music track without video streams hides video presets cleanly
        val audioOnlyJson = """
            {
              "track": "Nightcall",
              "artist": "Kavinsky",
              "duration": 258,
              "webpage_url": "https://music.youtube.com/watch?v=kav",
              "formats": [
                {"vcodec": "none", "acodec": "mp4a.40.2", "ext": "m4a", "abr": 129.0},
                {"vcodec": "none", "acodec": "opus", "ext": "webm", "abr": 154.0}
              ]
            }
        """.trimIndent()
        val parsedAudio = MetadataJsonParser.parseSingleVideo(audioOnlyJson, "https://music.youtube.com/watch?v=kav")
        assertNotNull(parsedAudio)
        assertTrue(parsedAudio!!.formatsProbed)
        assertTrue(parsedAudio.availableVideoPresets.isEmpty())
        assertEquals("AAC 129k", parsedAudio.musicFormatNotes[QualityPreset.AUDIO_M4A])
        assertEquals("154k", parsedAudio.musicFormatNotes[QualityPreset.AUDIO_OPUS])
    }

    @Test
    fun urlValidatorParsesSingleMultiplePlaylistAndSharedText() {
        assertTrue(UrlValidator.isYouTubeUrl("https://music.youtube.com/watch?v=abc123"))
        assertTrue(UrlValidator.isYouTubeUrl("https://youtu.be/xyz"))
        assertFalse(UrlValidator.isYouTubeUrl("https://example.com/file.mp4"))

        assertTrue(UrlValidator.isPlaylistUrl("https://www.youtube.com/playlist?list=PL123456"))
        assertTrue(UrlValidator.isPlaylistUrl("https://music.youtube.com/watch?v=abc&list=RDAMVM123"))
        assertFalse(UrlValidator.isPlaylistUrl("https://youtu.be/xyz"))
        assertTrue(UrlValidator.isPurePlaylistUrl("https://www.youtube.com/playlist?list=PL123456"))
        assertTrue(UrlValidator.isPurePlaylistUrl("https://music.youtube.com/playlist?list=OLAK5uy_abc"))
        assertFalse(UrlValidator.isPurePlaylistUrl("https://music.youtube.com/watch?v=abc&list=RDAMVM123"))

        val batch = UrlValidator.parseUrls("https://youtu.be/one\nhttps://music.youtube.com/watch?v=two")
        assertEquals(
            listOf("https://youtu.be/one", "https://music.youtube.com/watch?v=two"),
            batch,
        )

        val shared = UrlValidator.parseUrls("Check out this track! https://youtu.be/abc123")
        assertEquals(listOf("https://youtu.be/abc123"), shared)
    }

    @Test
    fun formattersProduceExpectedStrings() {
        assertEquals("02:05", formatDurationSeconds(125))
        assertEquals("1:01:01", formatDurationSeconds(3661))
        assertEquals("00:00", formatDurationMillis(0L))
        assertEquals("512 KB", formatFileSize(512 * 1024L))
        assertEquals("2.5 MB", formatFileSize((2.5 * 1024 * 1024).toLong()))
    }
}
