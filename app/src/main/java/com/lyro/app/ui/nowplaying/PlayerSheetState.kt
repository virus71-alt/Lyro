package com.lyro.app.ui.nowplaying

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import com.lyro.app.core.haptics.LyroHapticsController

enum class PlayerSheetValue {
    COLLAPSED,
    EXPANDED
}

class PlayerSheetState(
    initialValue: PlayerSheetValue,
    val animatableOffset: Animatable<Float, AnimationVector1D> = Animatable(
        if (initialValue == PlayerSheetValue.EXPANDED) 0f else 2500f
    )
) {
    var currentValue by mutableStateOf(initialValue)
        internal set

    var targetValue by mutableStateOf(initialValue)
        internal set

    var collapseDistance by mutableFloatStateOf(0f)

    val dragOffsetY: Float
        get() = animatableOffset.value

    val collapseProgress: Float
        get() = if (collapseDistance > 0f) {
            (dragOffsetY / collapseDistance).coerceIn(0f, 1f)
        } else {
            if (currentValue == PlayerSheetValue.EXPANDED) 0f else 1f
        }

    val isVisible: Boolean
        get() = collapseProgress < 1f || currentValue == PlayerSheetValue.EXPANDED

    val isExpanded: Boolean
        get() = currentValue == PlayerSheetValue.EXPANDED && dragOffsetY == 0f

    val isCollapsed: Boolean
        get() = currentValue == PlayerSheetValue.COLLAPSED && dragOffsetY >= collapseDistance

    // Threshold flag to prevent repeated haptic triggers while hovering around threshold
    var hasCrossedThreshold by mutableStateOf(false)
        private set

    val threshold: Float
        get() = collapseDistance * 0.22f // ~22% distance threshold

    suspend fun snapTo(newOffset: Float) {
        val maxDist = if (collapseDistance > 0f) collapseDistance else 2500f
        val clamped = newOffset.coerceIn(0f, maxDist)
        animatableOffset.snapTo(clamped)
        if (clamped == 0f) {
            currentValue = PlayerSheetValue.EXPANDED
            targetValue = PlayerSheetValue.EXPANDED
        } else if (clamped >= maxDist && maxDist > 0f) {
            currentValue = PlayerSheetValue.COLLAPSED
            targetValue = PlayerSheetValue.COLLAPSED
        }
    }

    suspend fun expand(velocity: Float = 0f) {
        targetValue = PlayerSheetValue.EXPANDED
        animatableOffset.animateTo(
            targetValue = 0f,
            initialVelocity = velocity,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
        currentValue = PlayerSheetValue.EXPANDED
        hasCrossedThreshold = false
    }

    suspend fun collapse(velocity: Float = 0f, onCollapsed: (() -> Unit)? = null) {
        targetValue = PlayerSheetValue.COLLAPSED
        val maxDist = if (collapseDistance > 0f) collapseDistance else 2500f
        val remainingDistance = (maxDist - dragOffsetY).coerceAtLeast(0f)
        val duration = ((remainingDistance / maxDist) * 260)
            .toInt()
            .coerceIn(180, 280)

        animatableOffset.animateTo(
            targetValue = maxDist,
            initialVelocity = velocity,
            animationSpec = tween(
                durationMillis = duration,
                easing = FastOutSlowInEasing
            )
        )
        currentValue = PlayerSheetValue.COLLAPSED
        hasCrossedThreshold = false
        onCollapsed?.invoke()
    }

    suspend fun handleRelease(
        velocity: Float,
        haptics: LyroHapticsController? = null,
        onCollapsed: (() -> Unit)? = null
    ) {
        val shouldCollapse = if (velocity < -500f) {
            false
        } else {
            (dragOffsetY >= threshold) || (velocity > 1000f)
        }
        if (shouldCollapse) {
            haptics?.click()
            collapse(velocity = velocity.coerceAtLeast(0f), onCollapsed = onCollapsed)
        } else {
            expand(velocity = velocity)
        }
    }

    fun checkThresholdHaptic(offset: Float, haptics: LyroHapticsController?) {
        val t = threshold
        if (t > 0f) {
            if (offset >= t && !hasCrossedThreshold) {
                hasCrossedThreshold = true
                haptics?.tick()
            } else if (offset < t * 0.75f && hasCrossedThreshold) {
                hasCrossedThreshold = false
            }
        }
    }
}

@Composable
fun rememberPlayerSheetState(
    initialValue: PlayerSheetValue = PlayerSheetValue.COLLAPSED
): PlayerSheetState {
    return remember {
        PlayerSheetState(initialValue)
    }
}
