package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    var startAnimation by remember { mutableStateOf(false) }

    // Icon Entrance Animation: Smooth subtle scale-up and alpha fade-in
    val scaleAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.45f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "LogoScale"
    )

    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1.0f else 0.0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "LogoAlpha"
    )

    // Subtle neon pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "NeonPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    // Glow pulse animation
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowPulse"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2400)
        onSplashComplete()
    }

    val backgroundColor = if (isDarkTheme) Color(0xFF080910) else Color(0xFFF6F8FC)
    val textColor = if (isDarkTheme) Color.White else Color(0xFF0F111E)
    val tagColor = if (isDarkTheme) Color(0xFF00F5FF) else Color(0xFF0091EA)
    val glowColors = if (isDarkTheme) {
        listOf(
            Color(0xFF00F5FF).copy(alpha = 0.35f * glowAlpha),
            Color(0xFFBD00FF).copy(alpha = 0.20f * glowAlpha),
            Color.Transparent
        )
    } else {
        listOf(
            Color(0xFF00B4D8).copy(alpha = 0.25f * glowAlpha),
            Color(0xFF7209B7).copy(alpha = 0.12f * glowAlpha),
            Color.Transparent
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        // Subtle ambient radial background glow behind center branding
        Box(
            modifier = Modifier
                .size(320.dp)
                .background(
                    Brush.radialGradient(colors = glowColors),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Elevated Brand Icon Container
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(scaleAnim * pulseScale)
                    .shadow(
                        elevation = if (isDarkTheme) 24.dp else 12.dp,
                        shape = CircleShape,
                        spotColor = if (isDarkTheme) Color(0xFF00F5FF) else Color(0xFF0091EA)
                    )
                    .clip(CircleShape)
                    .background(
                        if (isDarkTheme) Color(0xE60D0E1C) else Color(0xF2FFFFFF)
                    )
                    .border(
                        width = 2.dp,
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF00F5FF),
                                Color(0xFFBD00FF),
                                Color(0xFF00F5FF)
                            )
                        ),
                        shape = CircleShape
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_ongaku_vector_emblem),
                    contentDescription = "Ongaku7 Vector Brand Emblem",
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(alphaAnim)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // High-Resolution App Title with clean, premium typography
            Text(
                text = "Ongaku7",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                color = textColor,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.scale(scaleAnim)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "HYBRID CORE AUDIO ENGINE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = tagColor,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.scale(scaleAnim)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Sleek loading indicator
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                color = if (isDarkTheme) Color(0xFF00F5FF) else Color(0xFF0091EA),
                trackColor = if (isDarkTheme) Color(0x3300F5FF) else Color(0x220091EA),
                strokeWidth = 2.5.dp
            )
        }
    }
}
