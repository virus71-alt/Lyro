package com.lyro.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LyroCornerRadius = 12.dp
val LyroPillShape = RoundedCornerShape(24.dp)
val LyroCardShape = RoundedCornerShape(14.dp)

/**
 * Modern Minimalist Card Surface.
 * Subtle, elevated dark surface without bulky outlines or offset cartoon shadows.
 */
@Composable
fun LyroCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = LyroSurface,
    borderColor: Color = LyroCardBorder,
    borderWidth: Dp = 1.dp,
    shape: Shape = LyroCardShape,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor, shape)
            .border(borderWidth, borderColor, shape),
        content = content
    )
}

/**
 * Modern Minimalist Button.
 * Smooth tactile feedback with subtle rounded corners and clean content.
 */
@Composable
fun LyroButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = LyroAccent,
    contentColor: Color = Color.Black,
    shape: Shape = LyroPillShape,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val effectiveBg = if (enabled) backgroundColor else LyroSurfaceHighlight
    val effectiveContentColor = if (enabled) contentColor else LyroTextMuted

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides effectiveContentColor
    ) {
        Row(
            modifier = modifier
                .clip(shape)
                .background(effectiveBg, shape)
                .clickable(
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Modern Minimalist Icon Button.
 * Clean, touch-friendly icon buttons.
 */
@Composable
fun LyroIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    contentColor: Color = LyroTextPrimary,
    size: Dp = 40.dp,
    shape: Shape = CircleShape,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides contentColor
    ) {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(backgroundColor, shape)
                .clickable(
                    enabled = enabled,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/**
 * Minimalist Badge / Pill Label.
 */
@Composable
fun LyroBadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = LyroSurfaceHighlight,
    textColor: Color = LyroTextSecondary
) {
    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.2.sp
        )
    }
}

/**
 * Modern Filter / Category Chip.
 */
@Composable
fun LyroChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = LyroAccent,
    inactiveColor: Color = LyroSurfaceElevated
) {
    val shape = LyroPillShape
    val bg = if (selected) activeColor else inactiveColor
    val textColor = if (selected) Color.Black else LyroTextSecondary

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}

/**
 * Clean Section Header.
 */
@Composable
fun LyroSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = LyroTextPrimary
) {
    Text(
        text = text,
        style = LyroTypography.headlineMedium,
        color = textColor,
        modifier = modifier.padding(vertical = 4.dp)
    )
}
