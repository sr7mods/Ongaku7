package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DropdownOption<T>(
    val value: T,
    val title: String,
    val subtitle: String? = null,
    val badge: String? = null,
    val icon: ImageVector? = null
)

/**
 * Premium glassmorphic dropdown card with spring-physics animated expansion,
 * rotating chevron indicator, glowing active pill, and micro-bounce item interactions.
 */
@Composable
fun <T> PremiumDropdownCard(
    title: String,
    selectedOption: T,
    options: List<DropdownOption<T>>,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accentColor: Color = Color(0xFF00F5FF)
) {
    var expanded by remember { mutableStateOf(false) }
    val currentSelected = options.find { it.value == selectedOption } ?: options.firstOrNull()

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "dropdown_chevron"
    )

    val borderGlowAlpha by animateFloatAsState(
        targetValue = if (expanded) 0.8f else 0.25f,
        animationSpec = tween(durationMillis = 250),
        label = "dropdown_border_glow"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF141526).copy(alpha = 0.92f),
                        Color(0xFF0B0C16).copy(alpha = 0.96f)
                    )
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = borderGlowAlpha),
                        Color(0xFFBD00FF).copy(alpha = borderGlowAlpha * 0.7f),
                        Color.White.copy(alpha = 0.10f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        // Dropdown Header (Clickable Trigger)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = accentColor)
                ) {
                    expanded = !expanded
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = Color.Gray,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Active State Pill Indicator
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = currentSelected?.title ?: "",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                // Rotating Chevron Button
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = if (expanded) accentColor else Color.LightGray,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(chevronRotation)
                    )
                }
            }
        }

        // Fluid Animated Options List Container
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(220)),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeOut(animationSpec = tween(180))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )
                Spacer(modifier = Modifier.height(4.dp))

                options.forEach { option ->
                    val isSelected = option.value == selectedOption
                    DropdownOptionItem(
                        option = option,
                        isSelected = isSelected,
                        accentColor = accentColor,
                        onClick = {
                            onOptionSelected(option.value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> DropdownOptionItem(
    option: DropdownOption<T>,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "item_press_scale"
    )

    val backgroundBrush = if (isSelected) {
        Brush.horizontalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.22f),
                Color(0xFFBD00FF).copy(alpha = 0.12f)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0x0AFFFFFF),
                Color(0x04FFFFFF)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundBrush)
            .border(
                width = 1.dp,
                color = if (isSelected) accentColor.copy(alpha = 0.65f) else Color(0x12FFFFFF),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accentColor)
            ) {
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Radio Check Circle
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) accentColor else Color.Transparent)
                        .border(
                            1.5.dp,
                            if (isSelected) accentColor else Color.Gray.copy(alpha = 0.6f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = Color(0xFF070810),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                if (option.icon != null) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        tint = if (isSelected) accentColor else Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = option.title,
                        color = if (isSelected) Color.White else Color.LightGray,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    if (option.subtitle != null) {
                        Text(
                            text = option.subtitle,
                            color = if (isSelected) accentColor.copy(alpha = 0.85f) else Color.Gray,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (option.badge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x22FFFFFF))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = option.badge,
                        color = Color.LightGray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Compact premium dropdown specifically crafted for in-line controls, such as audio parameters.
 */
@Composable
fun <T> CompactPremiumDropdown(
    selectedOption: T,
    options: List<DropdownOption<T>>,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF00F5FF)
) {
    var expanded by remember { mutableStateOf(false) }
    val currentSelected = options.find { it.value == selectedOption } ?: options.firstOrNull()

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "compact_dropdown_chevron"
    )

    Column(modifier = modifier) {
        // Pill Button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = if (expanded) 0.30f else 0.15f),
                            Color(0xFFBD00FF).copy(alpha = 0.12f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = if (expanded) accentColor else accentColor.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = accentColor)
                ) {
                    expanded = !expanded
                }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = currentSelected?.title ?: "",
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(chevronRotation)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(tween(180)),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeOut(tween(150))
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F101C))
                    .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                options.forEach { opt ->
                    val isSelected = opt.value == selectedOption
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable {
                                onOptionSelected(opt.value)
                                expanded = false
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = opt.title,
                                color = if (isSelected) accentColor else Color.LightGray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
