package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Premium background drawing a subtle cosmic, cyberpunk neon gradient glow.
 * Leverages an infinite transition to animate dual neon cyan and purple accent blobs.
 */
@Composable
fun BackgroundGlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "GlowAnimation")

    // Gentle motion for the cyan blob
    val cyanAnimX by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cyanX"
    )
    val cyanAnimY by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cyanY"
    )

    // Gentle motion for the purple blob
    val purpleAnimX by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "purpleX"
    )
    val purpleAnimY by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "purpleY"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070710)) // Super dark obsidian cosmic backdrop
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@Canvas

            // 1. Static ambient background radial gradient
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x1A6A0DAD), Color(0x00000000)),
                    center = Offset(width / 2, height / 2),
                    radius = width
                )
            )

            // 2. Animated Cyan Blob (Cyberpunk Neon vibe)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x2E00F5FF), // Cyberpunk Neon Cyan
                        Color(0x0000F5FF)
                    ),
                    center = Offset(width * cyanAnimX, height * cyanAnimY),
                    radius = width * 0.55f
                ),
                center = Offset(width * cyanAnimX, height * cyanAnimY),
                radius = width * 0.55f
            )

            // 3. Animated Magenta/Purple Blob (Glow Accent)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x33BD00FF), // Cyberpunk Hot Purple
                        Color(0x00BD00FF)
                    ),
                    center = Offset(width * purpleAnimX, height * purpleAnimY),
                    radius = width * 0.6f
                ),
                center = Offset(width * purpleAnimX, height * purpleAnimY),
                radius = width * 0.6f
            )
        }

        content()
    }
}
