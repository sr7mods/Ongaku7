package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable Glassmorphic Container that encapsulates the Material 3 Glassy theme.
 * Keeps child content crystal clear and sharp by removing full container blur,
 * utilizing a high-contrast semi-transparent surface overlay and glowing borders instead.
 */
@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0x29FFFFFF), // ~16% white gloss
                        Color(0x0F000000)  // ~6% deep black contrast
                    )
                )
            )
            .border(
                width = borderWidth,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x4DFFFFFF), // 30% alpha white for bright glassy borders
                        Color(0x1AFFFFFF), // 10% alpha white
                        Color(0x10000000)  // 6% black shadow depth
                    )
                ),
                shape = shape
            )
    ) {
        content()
    }
}

