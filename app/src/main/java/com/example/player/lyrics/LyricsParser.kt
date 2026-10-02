package com.example.player.lyrics

import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File

object LyricsParser {

    private const val TAG = "LyricsParser"

    // Regex matching LRC tags like [01:23.45], [01:23.456], or [01:23]
    private val LRC_TIMESTAMP_REGEX = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")

    // Regex matching SRT timestamp lines like 00:01:23,456 --> 00:01:28,000
    private val SRT_TIMESTAMP_REGEX = Regex(
        "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})"
    )

    private val HTML_TAG_REGEX = Regex("<[^>]*>")

    /**
     * Finds and loads lyrics for an audio track path, testing external files first (.lrc, .srt, .txt)
     * and falling back to embedded ID3 tags.
     */
    fun loadLyricsForAudio(audioPath: String): LyricsResult? {
        try {
            val audioFile = File(audioPath)
            val parentDir = audioFile.parentFile

            // 1. Check local directory for sidecar files: .lrc, .srt, .txt
            if (parentDir != null && parentDir.exists() && parentDir.isDirectory) {
                val baseName = audioFile.nameWithoutExtension.lowercase()
                val candidateFiles = parentDir.listFiles() ?: emptyArray()

                // Prioritize .lrc > .srt > .txt
                val lrcFile = candidateFiles.firstOrNull {
                    it.isFile && it.nameWithoutExtension.equals(baseName, ignoreCase = true) &&
                            it.extension.equals("lrc", ignoreCase = true)
                }
                if (lrcFile != null) {
                    val parsed = parseLrc(lrcFile.readText(), lrcFile.name)
                    if (parsed != null) return parsed
                }

                val srtFile = candidateFiles.firstOrNull {
                    it.isFile && it.nameWithoutExtension.equals(baseName, ignoreCase = true) &&
                            it.extension.equals("srt", ignoreCase = true)
                }
                if (srtFile != null) {
                    val parsed = parseSrt(srtFile.readText(), srtFile.name)
                    if (parsed != null) return parsed
                }

                val txtFile = candidateFiles.firstOrNull {
                    it.isFile && it.nameWithoutExtension.equals(baseName, ignoreCase = true) &&
                            it.extension.equals("txt", ignoreCase = true)
                }
                if (txtFile != null) {
                    val content = txtFile.readText()
                    return parseAuto(content, txtFile.name)
                }
            }

            // 2. Embedded ID3 metadata lyrics
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(audioPath)
                val embeddedLyrics = retriever.extractMetadata(1000) // METADATA_KEY_LYRICS
                if (!embeddedLyrics.isNullOrBlank()) {
                    return parseAuto(embeddedLyrics, "Embedded ID3 Tag")
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Embedded lyrics retrieval error: ${t.message}")
            } finally {
                try { retriever.release() } catch (_: Throwable) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading lyrics: ${e.message}")
        }
        return null
    }

    /**
     * Auto-detect format from raw content and parse into LyricsResult.
     */
    fun parseAuto(rawContent: String, sourceName: String = "Raw"): LyricsResult {
        val cleanContent = rawContent.removePrefix("\uFEFF").trim()

        if (cleanContent.contains("-->") && SRT_TIMESTAMP_REGEX.containsMatchIn(cleanContent)) {
            val srt = parseSrt(cleanContent, sourceName)
            if (srt != null) return srt
        }

        if (LRC_TIMESTAMP_REGEX.containsMatchIn(cleanContent)) {
            val lrc = parseLrc(cleanContent, sourceName)
            if (lrc != null) return lrc
        }

        return parsePlainText(cleanContent, sourceName)
    }

    /**
     * Parses standard LRC format with timestamps [mm:ss.xx] or [mm:ss.xxx].
     */
    fun parseLrc(rawContent: String, sourceName: String = "LRC File"): LyricsResult? {
        val cleanContent = rawContent.removePrefix("\uFEFF")
        val lines = cleanContent.lines()
        val lyricItems = mutableListOf<Pair<Long, String>>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Skip ID3 tags like [ar:Artist], [ti:Title], [al:Album], [offset:100], etc.
            if (trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") ||
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") ||
                trimmed.startsWith("[offset:") || trimmed.startsWith("[length:")
            ) {
                continue
            }

            val matches = LRC_TIMESTAMP_REGEX.findAll(trimmed).toList()
            if (matches.isEmpty()) continue

            // Strip timestamps to get the lyric line text
            val textOnly = trimmed.replace(LRC_TIMESTAMP_REGEX, "").trim()

            for (match in matches) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val msStr = match.groupValues.getOrNull(3) ?: ""
                val ms = when {
                    msStr.isEmpty() -> 0L
                    msStr.length == 1 -> msStr.toLong() * 100L
                    msStr.length == 2 -> msStr.toLong() * 10L
                    else -> msStr.take(3).toLong()
                }
                val totalMs = min * 60_000L + sec * 1_000L + ms
                lyricItems.add(Pair(totalMs, if (textOnly.isEmpty()) "♪" else textOnly))
            }
        }

        if (lyricItems.isEmpty()) {
            return null
        }

        lyricItems.sortBy { it.first }

        val resultLines = lyricItems.mapIndexed { index, pair ->
            val endTime = if (index < lyricItems.size - 1) {
                lyricItems[index + 1].first
            } else {
                pair.first + 6000L
            }
            LyricLine(
                id = index,
                startTimeMs = pair.first,
                endTimeMs = endTime,
                text = pair.second,
                isSynced = true
            )
        }

        return LyricsResult(
            isSynced = true,
            lines = resultLines,
            rawText = cleanContent,
            format = LyricsFormat.LRC,
            sourceDescription = sourceName
        )
    }

    /**
     * Parses SRT subtitle files with time intervals 00:00:00,000 --> 00:00:02,000.
     */
    fun parseSrt(rawContent: String, sourceName: String = "SRT Subtitle"): LyricsResult? {
        val cleanContent = rawContent.removePrefix("\uFEFF").replace("\r\n", "\n").replace("\r", "\n")
        val blocks = cleanContent.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
        val lyricItems = mutableListOf<LyricLine>()
        var idCounter = 0

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < 2) continue

            // Look for the timestamp line
            var timeLineIndex = -1
            var startMs = -1L
            var endMs = -1L

            for (i in lines.indices) {
                val match = SRT_TIMESTAMP_REGEX.find(lines[i])
                if (match != null) {
                    val sH = match.groupValues[1].toLongOrNull() ?: 0L
                    val sM = match.groupValues[2].toLongOrNull() ?: 0L
                    val sS = match.groupValues[3].toLongOrNull() ?: 0L
                    val sMs = match.groupValues[4].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                    startMs = sH * 3600_000L + sM * 60_000L + sS * 1_000L + sMs

                    val eH = match.groupValues[5].toLongOrNull() ?: 0L
                    val eM = match.groupValues[6].toLongOrNull() ?: 0L
                    val eS = match.groupValues[7].toLongOrNull() ?: 0L
                    val eMs = match.groupValues[8].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                    endMs = eH * 3600_000L + eM * 60_000L + eS * 1_000L + eMs

                    timeLineIndex = i
                    break
                }
            }

            if (timeLineIndex != -1 && startMs >= 0L) {
                val textLines = lines.drop(timeLineIndex + 1)
                val rawText = textLines.joinToString(" ").replace(HTML_TAG_REGEX, "").trim()
                if (rawText.isNotEmpty()) {
                    lyricItems.add(
                        LyricLine(
                            id = idCounter++,
                            startTimeMs = startMs,
                            endTimeMs = endMs,
                            text = rawText,
                            isSynced = true
                        )
                    )
                }
            }
        }

        if (lyricItems.isEmpty()) return null

        lyricItems.sortBy { it.startTimeMs }

        return LyricsResult(
            isSynced = true,
            lines = lyricItems,
            rawText = cleanContent,
            format = LyricsFormat.SRT,
            sourceDescription = sourceName
        )
    }

    /**
     * Fallback for plain unsynced text.
     */
    fun parsePlainText(rawContent: String, sourceName: String = "Plain Text"): LyricsResult {
        val clean = rawContent.removePrefix("\uFEFF").trim()
        val lines = clean.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val resultLines = lines.mapIndexed { index, line ->
            LyricLine(
                id = index,
                startTimeMs = -1L,
                endTimeMs = -1L,
                text = line,
                isSynced = false
            )
        }

        return LyricsResult(
            isSynced = false,
            lines = resultLines,
            rawText = clean,
            format = LyricsFormat.PLAIN_TEXT,
            sourceDescription = sourceName
        )
    }

    /**
     * Formats milliseconds into readable mm:ss timestamp format.
     */
    fun formatTimestamp(ms: Long): String {
        if (ms < 0) return "--:--"
        val totalSec = ms / 1000L
        val min = totalSec / 60L
        val sec = totalSec % 60L
        return String.format("%02d:%02d", min, sec)
    }
}
