package com.lyro.app.ui.nowplaying

import kotlin.math.abs

/**
 * Shared logic and constants for Lyro's Sleep Timer dial and preset cycling.
 */
object SleepTimerUtils {
    const val MIN_MINUTES = 5
    const val MAX_MINUTES = 120
    const val STEP_MINUTES = 5
    const val DEFAULT_MINUTES = 15
    const val DEGREES_PER_STEP = 12f // 12° per 5-minute detent for buttery smooth, responsive feel

    val PRESETS = listOf(15, 30, 45, 60)

    /**
     * Cycles to the next quick preset based on the current timer.
     * Sequence: 15 → 30 → 45 → 60 → 15.
     * If current value is custom (e.g. 22 or 38), advances to the next logical preset above it.
     * If current >= 60 (e.g. 60 or 75), cycles back to 15.
     */
    fun getNextPreset(currentMinutesLeft: Int?): Int {
        if (currentMinutesLeft == null || currentMinutesLeft <= 0) {
            return PRESETS.first() // 15
        }
        val next = PRESETS.firstOrNull { it > currentMinutesLeft }
        return next ?: PRESETS.first()
    }

    /**
     * Normalizes angular difference between current and previous angles,
     * cleanly wrapping across ±180° boundary (e.g., 359° → 1°).
     */
    fun normalizeAngleDelta(currentAngle: Float, lastAngle: Float): Float {
        var delta = currentAngle - lastAngle
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        return delta
    }

    /**
     * Rounds a minute value to the nearest valid step within [min, max].
     */
    fun roundToNearestStep(
        minutes: Int,
        step: Int = STEP_MINUTES,
        min: Int = MIN_MINUTES,
        max: Int = MAX_MINUTES
    ): Int {
        val rounded = ((minutes + step / 2) / step) * step
        return rounded.coerceIn(min, max)
    }

    /**
     * Calculates the step change from accumulated angular rotation.
     * Returns Pair(stepDelta, remainingAccumulatedAngle).
     */
    fun calculateStepDelta(
        accumulatedAngle: Float,
        degreesPerStep: Float = DEGREES_PER_STEP
    ): Pair<Int, Float> {
        val steps = (accumulatedAngle / degreesPerStep).toInt()
        val remainder = accumulatedAngle - (steps * degreesPerStep)
        return Pair(steps, remainder)
    }
}
