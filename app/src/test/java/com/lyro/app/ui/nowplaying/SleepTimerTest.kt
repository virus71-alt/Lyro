package com.lyro.app.ui.nowplaying

import org.junit.Assert.*
import org.junit.Test

class SleepTimerTest {

    @Test
    fun testDefaultPresetWhenNoTimerActive() {
        assertEquals(15, SleepTimerUtils.getNextPreset(null))
        assertEquals(15, SleepTimerUtils.getNextPreset(0))
        assertEquals(15, SleepTimerUtils.getNextPreset(-5))
    }

    @Test
    fun testPresetCycleSequence() {
        // Standard sequence: 15 → 30 → 45 → 60 → 15
        assertEquals(30, SleepTimerUtils.getNextPreset(15))
        assertEquals(45, SleepTimerUtils.getNextPreset(30))
        assertEquals(60, SleepTimerUtils.getNextPreset(45))
        assertEquals(15, SleepTimerUtils.getNextPreset(60))
    }

    @Test
    fun testCustomValueAdvancesToNextLogicalPreset() {
        // Custom value advances to the next preset above it:
        // 22 → 30
        assertEquals(30, SleepTimerUtils.getNextPreset(22))
        // 38 → 45
        assertEquals(45, SleepTimerUtils.getNextPreset(38))
        // 52 → 60
        assertEquals(60, SleepTimerUtils.getNextPreset(52))
        // After >= 60, cycles to 15
        assertEquals(15, SleepTimerUtils.getNextPreset(75))
        assertEquals(15, SleepTimerUtils.getNextPreset(120))
    }

    @Test
    fun testAngleNormalizationAcross360Wrap() {
        // Regular forward rotation
        val deltaNormalForward = SleepTimerUtils.normalizeAngleDelta(45f, 30f)
        assertEquals(15f, deltaNormalForward, 0.001f)

        // Regular backward rotation
        val deltaNormalBackward = SleepTimerUtils.normalizeAngleDelta(30f, 45f)
        assertEquals(-15f, deltaNormalBackward, 0.001f)

        // Clockwise crossing 0°/360° boundary (e.g. from 355° to 5° is +10°)
        val deltaWrapClockwise = SleepTimerUtils.normalizeAngleDelta(5f, 355f)
        assertEquals(10f, deltaWrapClockwise, 0.001f)

        // Counter-clockwise crossing 0°/360° boundary (e.g. from 5° to 355° is -10°)
        val deltaWrapCounter = SleepTimerUtils.normalizeAngleDelta(355f, 5f)
        assertEquals(-10f, deltaWrapCounter, 0.001f)
    }

    @Test
    fun testRoundToNearestStep() {
        assertEquals(5, SleepTimerUtils.roundToNearestStep(4))
        assertEquals(5, SleepTimerUtils.roundToNearestStep(5))
        assertEquals(10, SleepTimerUtils.roundToNearestStep(8))
        assertEquals(15, SleepTimerUtils.roundToNearestStep(13))
        assertEquals(20, SleepTimerUtils.roundToNearestStep(22))
        assertEquals(25, SleepTimerUtils.roundToNearestStep(24))
        assertEquals(120, SleepTimerUtils.roundToNearestStep(125))
    }

    @Test
    fun testCalculateStepDelta() {
        val (step1, rem1) = SleepTimerUtils.calculateStepDelta(15f, 15f)
        assertEquals(1, step1)
        assertEquals(0f, rem1, 0.001f)

        val (step2, rem2) = SleepTimerUtils.calculateStepDelta(35f, 15f)
        assertEquals(2, step2)
        assertEquals(5f, rem2, 0.001f)

        val (step3, rem3) = SleepTimerUtils.calculateStepDelta(-20f, 15f)
        assertEquals(-1, step3)
        assertEquals(-5f, rem3, 0.001f)

        // Test with default DEGREES_PER_STEP (12f)
        val (stepDef, remDef) = SleepTimerUtils.calculateStepDelta(26f)
        assertEquals(2, stepDef)
        assertEquals(2f, remDef, 0.001f)
    }
}
