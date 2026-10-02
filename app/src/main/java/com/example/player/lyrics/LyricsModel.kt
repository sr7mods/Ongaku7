package com.example.player.lyrics

data class LyricLine(
    val id: Int,
    val startTimeMs: Long,
    val endTimeMs: Long = -1L,
    val text: String,
    val isSynced: Boolean = true
)

enum class LyricsFormat {
    LRC,
    SRT,
    PLAIN_TEXT,
    EMBEDDED
}

data class LyricsResult(
    val isSynced: Boolean,
    val lines: List<LyricLine>,
    val rawText: String,
    val format: LyricsFormat,
    val sourceDescription: String
)
