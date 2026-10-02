package com.example

import com.example.player.AppIconManager
import com.example.player.lyrics.LyricsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testLrcParserWithTimestamps() {
        val sampleLrc = """
            [ti:Test Track]
            [ar:Test Artist]
            [00:01.50]First line of lyrics
            [00:05.25]Second line of lyrics
            [00:10.00]Third line of lyrics
        """.trimIndent()

        val result = LyricsParser.parseAuto(sampleLrc, "test.lrc")
        assertTrue("Lyrics should be time synced", result.isSynced)
        assertEquals(3, result.lines.size)

        assertEquals("First line of lyrics", result.lines[0].text)
        assertEquals(1500L, result.lines[0].startTimeMs)
        assertEquals(5250L, result.lines[0].endTimeMs)

        assertEquals("Second line of lyrics", result.lines[1].text)
        assertEquals(5250L, result.lines[1].startTimeMs)
        assertEquals(10000L, result.lines[1].endTimeMs)
    }

    @Test
    fun testSrtParserWithSubtitles() {
        val sampleSrt = """
            1
            00:00:01,000 --> 00:00:04,500
            Welcome to Ongaku 7

            2
            00:00:05,000 --> 00:00:08,200
            Cyberpunk Studio Audio
        """.trimIndent()

        val result = LyricsParser.parseAuto(sampleSrt, "test.srt")
        assertTrue("SRT should be time synced", result.isSynced)
        assertEquals(2, result.lines.size)
        assertEquals("Welcome to Ongaku 7", result.lines[0].text)
        assertEquals(1000L, result.lines[0].startTimeMs)
        assertEquals(4500L, result.lines[0].endTimeMs)
    }

    @Test
    fun testAppIconManagerOptions() {
        val options = AppIconManager.ICON_OPTIONS
        assertEquals(5, options.size)
        val defaultOpt = options.firstOrNull { it.id == AppIconManager.ICON_DEFAULT }
        assertNotNull(defaultOpt)
        assertEquals("Default Icon", defaultOpt?.title)
    }

    @Test
    fun testTempoPitchPointOneStepping() {
        var tempo = 1.0f
        tempo = ((Math.round((tempo + 0.1f) * 10f)) / 10f).coerceIn(0.1f, 2.0f)
        assertEquals(1.1f, tempo, 0.001f)

        tempo = ((Math.round((tempo - 0.1f) * 10f)) / 10f).coerceIn(0.1f, 2.0f)
        assertEquals(1.0f, tempo, 0.001f)

        var pitch = 1.0f
        pitch = ((Math.round((pitch - 0.1f) * 10f)) / 10f).coerceIn(0.1f, 2.0f)
        assertEquals(0.9f, pitch, 0.001f)
    }
}

