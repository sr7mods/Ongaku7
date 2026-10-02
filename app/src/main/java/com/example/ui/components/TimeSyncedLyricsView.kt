package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.lyrics.LyricLine
import com.example.player.lyrics.LyricsFormat
import com.example.player.lyrics.LyricsParser
import com.example.player.lyrics.LyricsResult

@Composable
fun TimeSyncedLyricsView(
    lyricsResult: LyricsResult?,
    currentPositionMs: Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = Color(0xFF00F5FF),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "PARSING LYRICS TIMELINE...",
                    color = Color(0xFF00F5FF).copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
        }
        return
    }

    if (lyricsResult == null || lyricsResult.lines.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SubtitlesOff,
                    contentDescription = "No Lyrics",
                    tint = Color.Gray.copy(alpha = 0.6f),
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "NO LYRICS FOUND",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Place a .lrc, .srt, or .txt file in the same folder as this song, or ensure embedded ID3 tags are present.",
                    color = Color.LightGray.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }
        return
    }

    // Format badge & source chip
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Source Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when (lyricsResult.format) {
                    LyricsFormat.LRC -> Color(0x3300F5FF)
                    LyricsFormat.SRT -> Color(0x33BD00FF)
                    LyricsFormat.EMBEDDED -> Color(0x3300E676)
                    LyricsFormat.PLAIN_TEXT -> Color(0x22FFFFFF)
                },
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when (lyricsResult.format) {
                        LyricsFormat.LRC -> Color(0xFF00F5FF).copy(alpha = 0.6f)
                        LyricsFormat.SRT -> Color(0xFFBD00FF).copy(alpha = 0.6f)
                        LyricsFormat.EMBEDDED -> Color(0xFF00E676).copy(alpha = 0.6f)
                        LyricsFormat.PLAIN_TEXT -> Color.White.copy(alpha = 0.3f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (lyricsResult.isSynced) Icons.Default.Timer else Icons.Default.Subtitles,
                        contentDescription = null,
                        tint = when (lyricsResult.format) {
                            LyricsFormat.LRC -> Color(0xFF00F5FF)
                            LyricsFormat.SRT -> Color(0xFFBD00FF)
                            LyricsFormat.EMBEDDED -> Color(0xFF00E676)
                            LyricsFormat.PLAIN_TEXT -> Color.LightGray
                        },
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${lyricsResult.format.name} ${if (lyricsResult.isSynced) "SYNCED" else "PLAIN"}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Text(
                text = "Tap any line to seek",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (lyricsResult.isSynced) {
            SyncedLyricsList(
                lines = lyricsResult.lines,
                currentPositionMs = currentPositionMs,
                isPlaying = isPlaying,
                onSeekTo = onSeekTo,
                modifier = Modifier.weight(1f)
            )
        } else {
            UnsyncedLyricsList(
                lines = lyricsResult.lines,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SyncedLyricsList(
    lines: List<LyricLine>,
    currentPositionMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine active line index based on playback position
    val activeIndex by remember(currentPositionMs, lines) {
        derivedStateOf {
            val idx = lines.indexOfLast { it.startTimeMs <= currentPositionMs }
            if (idx == -1 && lines.isNotEmpty() && currentPositionMs < lines.first().startTimeMs) {
                0
            } else {
                idx
            }
        }
    }

    val listState = rememberLazyListState()

    // Smooth auto-scrolling: center active lyric line on screen
    LaunchedEffect(activeIndex) {
        if (activeIndex in lines.indices) {
            try {
                // Offset of around -160 to center the active line vertically
                listState.animateScrollToItem(
                    index = activeIndex,
                    scrollOffset = -180
                )
            } catch (_: Exception) {
                // Graceful fallback
            }
        }
    }

    // Animated pulsating glow for active timestamped line
    val infiniteTransition = rememberInfiniteTransition(label = "LyricsActivePulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.70f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowPulse"
    )

    // Soft top and bottom edge gradient fade mask
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .testTag("lyrics_list_view"),
            contentPadding = PaddingValues(vertical = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(
                items = lines,
                key = { _, item -> item.id }
            ) { index, line ->
                val isActive = index == activeIndex

                SyncedLyricRow(
                    line = line,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    glowAlpha = if (isActive) glowAlpha else 1f,
                    onClick = {
                        if (line.startTimeMs >= 0) {
                            onSeekTo(line.startTimeMs)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SyncedLyricRow(
    line: LyricLine,
    isActive: Boolean,
    isPlaying: Boolean,
    glowAlpha: Float,
    onClick: () -> Unit
) {
    val targetTextColor by animateColorAsState(
        targetValue = if (isActive) Color.White else Color.LightGray.copy(alpha = 0.40f),
        animationSpec = tween(350),
        label = "LyricTextColor"
    )

    val targetBgColor by animateColorAsState(
        targetValue = if (isActive) Color(0x3300F5FF) else Color.Transparent,
        animationSpec = tween(350),
        label = "LyricBgColor"
    )

    val targetBorderColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFF00F5FF).copy(alpha = 0.7f * glowAlpha) else Color.Transparent,
        animationSpec = tween(350),
        label = "LyricBorderColor"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color(0xFF00F5FF)),
                onClick = onClick
            )
            .testTag("lyric_line_${line.id}"),
        shape = RoundedCornerShape(16.dp),
        color = targetBgColor,
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.5.dp, targetBorderColor) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timestamp indicator chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isActive) Color(0xFF00F5FF).copy(alpha = 0.25f * glowAlpha)
                        else Color.White.copy(alpha = 0.08f)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = LyricsParser.formatTimestamp(line.startTimeMs),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) Color(0xFF00F5FF).copy(alpha = glowAlpha) else Color.White.copy(alpha = 0.40f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Lyric line content
            Text(
                text = line.text,
                color = targetTextColor,
                fontSize = if (isActive) 19.sp else 15.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.Default,
                textAlign = TextAlign.Start,
                lineHeight = if (isActive) 26.sp else 22.sp,
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (isActive) glowAlpha else 0.40f)
            )

            if (isActive && isPlaying) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Active Playing",
                    tint = Color(0xFF00F5FF).copy(alpha = glowAlpha),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun UnsyncedLyricsList(
    lines: List<LyricLine>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        lines.forEach { line ->
            Text(
                text = line.text,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )
        }
    }
}
