package com.lyro.app.core.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val NeoCornerRadius = 10.dp
val NeoBorderWidth = 2.5.dp
val NeoDefaultShadow = 4.dp

/**
 * Neo-Brutalist Box with an authentic solid drop-shadow offset and crisp black border.
 */
@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = NeoWhite,
    borderColor: Color = NeoBlack,
    shadowColor: Color = NeoBlack,
    borderWidth: Dp = NeoBorderWidth,
    shadowOffset: Dp = NeoDefaultShadow,
    shape: Shape = RoundedCornerShape(NeoCornerRadius),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.padding(end = shadowOffset, bottom = shadowOffset)
    ) {
        // Hard-edged solid shadow underneath
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .background(shadowColor, shape)
        )

        // Main brutalist surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor, shape)
                .border(borderWidth, borderColor, shape)
                .clip(shape),
            content = content
        )
    }
}

/**
 * Tactile Neo-Brutal Button.
 * On press, the button physically depresses (shifts translation by shadowOffset)
 * and the shadow collapses, creating a realistic mechanical press sensation.
 */
@Composable
fun NeoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = NeoAcidGreen,
    contentColor: Color = NeoBlack,
    borderColor: Color = NeoBlack,
    shadowColor: Color = NeoBlack,
    borderWidth: Dp = NeoBorderWidth,
    shadowOffset: Dp = NeoDefaultShadow,
    shape: Shape = RoundedCornerShape(NeoCornerRadius),
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val currentOffset by animateDpAsState(
        targetValue = if (isPressed && enabled) 1.dp else shadowOffset,
        animationSpec = tween(durationMillis = 80),
        label = "neoButtonOffset"
    )

    val currentShift by animateDpAsState(
        targetValue = if (isPressed && enabled) (shadowOffset - 1.dp) else 0.dp,
        animationSpec = tween(durationMillis = 80),
        label = "neoButtonShift"
    )

    Box(
        modifier = modifier.padding(end = shadowOffset, bottom = shadowOffset)
    ) {
        // Hard shadow layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = currentOffset, y = currentOffset)
                .background(if (enabled) shadowColor else NeoGrayMedium, shape)
        )

        // Top button layer
        Row(
            modifier = Modifier
                .offset(x = currentShift, y = currentShift)
                .background(if (enabled) backgroundColor else NeoGrayLight, shape)
                .border(borderWidth, borderColor, shape)
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Neo-Brutalist Icon Button with tactile click feedback
 */
@Composable
fun NeoIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = NeoWhite,
    borderColor: Color = NeoBlack,
    shadowColor: Color = NeoBlack,
    borderWidth: Dp = NeoBorderWidth,
    shadowOffset: Dp = 3.dp,
    size: Dp = 44.dp,
    shape: Shape = RoundedCornerShape(8.dp),
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val currentOffset by animateDpAsState(
        targetValue = if (isPressed) 1.dp else shadowOffset,
        animationSpec = tween(durationMillis = 80),
        label = "neoIconOffset"
    )

    val currentShift by animateDpAsState(
        targetValue = if (isPressed) (shadowOffset - 1.dp) else 0.dp,
        animationSpec = tween(durationMillis = 80),
        label = "neoIconShift"
    )

    Box(
        modifier = modifier
            .size(size + shadowOffset)
            .padding(end = shadowOffset, bottom = shadowOffset)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .offset(x = currentOffset, y = currentOffset)
                .background(shadowColor, shape)
        )

        Box(
            modifier = Modifier
                .size(size)
                .offset(x = currentShift, y = currentShift)
                .background(backgroundColor, shape)
                .border(borderWidth, borderColor, shape)
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/**
 * Neo-Brutalist Badge / Pill Sticker
 */
@Composable
fun NeoBadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = NeoCyberYellow,
    textColor: Color = NeoBlack,
    borderColor: Color = NeoBlack
) {
    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.6.sp
        )
    }
}

/**
 * Neo-Brutalist Filter Chip
 */
@Composable
fun NeoChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = NeoAcidGreen,
    inactiveColor: Color = NeoWhite
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    val shadowOffset = if (selected) 2.dp else 1.dp

    Box(
        modifier = modifier.padding(end = 3.dp, bottom = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .background(NeoBlack, shape)
        )

        Box(
            modifier = Modifier
                .background(if (selected) activeColor else inactiveColor, shape)
                .border(2.dp, NeoBlack, shape)
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = NeoBlack,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}
