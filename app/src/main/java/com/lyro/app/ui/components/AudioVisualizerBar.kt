package com.lyro.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lyro.app.core.designsystem.NeoAcidGreen
import com.lyro.app.core.designsystem.NeoBlack

@Composable
fun AudioVisualizerBar(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = NeoAcidGreen,
    barCount: Int = 4,
    maxHeight: Dp = 20.dp,
    barWidth: Dp = 3.5.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audioBars")

    val anim1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b1"
    )

    val anim2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b2"
    )

    val anim3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b3"
    )

    val anim4 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b4"
    )

    val heights = listOf(anim1, anim2, anim3, anim4)

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0 until barCount) {
            val scale = if (isPlaying) heights[i % heights.size] else 0.25f
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(maxHeight * scale)
                    .background(barColor, RoundedCornerShape(2.dp))
                    .border(1.dp, NeoBlack, RoundedCornerShape(2.dp))
            )
        }
    }
}
