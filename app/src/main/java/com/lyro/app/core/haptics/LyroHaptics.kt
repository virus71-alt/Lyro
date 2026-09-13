package com.lyro.app.core.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * High-level semantic haptic feedback categories for Lyro.
 * Restrained, tactile, and natural without overusing vibration.
 */
enum class LyroHaptic {
    Selection,
    Click,
    StrongClick,
    Tick,
    LongPress
}

/**
 * Controller executing system haptic feedback on an Android [View].
 * Automatically respects device/system haptic settings and Lyro's app-level toggle.
 * Does not require the VIBRATE permission.
 */
class LyroHapticsController(
    private val view: View,
    private val isEnabled: () -> Boolean
) {
    fun perform(type: LyroHaptic) {
        if (!isEnabled()) return

        val constant = when (type) {
            LyroHaptic.Selection -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    HapticFeedbackConstants.CONTEXT_CLICK
                } else {
                    HapticFeedbackConstants.KEYBOARD_TAP
                }
            }
            LyroHaptic.Click -> {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
            LyroHaptic.StrongClick -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
            }
            LyroHaptic.Tick -> {
                // Subtle clock tick for dial detents and smooth scrubber intervals
                HapticFeedbackConstants.CLOCK_TICK
            }
            LyroHaptic.LongPress -> {
                HapticFeedbackConstants.LONG_PRESS
            }
        }

        view.performHapticFeedback(constant)
    }

    /**
     * Subtle light feedback for tab changes, chips, and toggles.
     */
    fun selection() = perform(LyroHaptic.Selection)

    /**
     * Standard tactile click for buttons, transport controls, and list item taps.
     */
    fun click() = perform(LyroHaptic.Click)

    /**
     * Confirm / release click when finishing a seek operation.
     */
    fun strongClick() = perform(LyroHaptic.StrongClick)

    /**
     * Crisp, microscopic detent tick for rotary wheel dragging.
     */
    fun tick() = perform(LyroHaptic.Tick)

    /**
     * Standard tactile feedback for long-press contextual menus.
     */
    fun longPress() = perform(LyroHaptic.LongPress)
}

/**
 * CompositionLocal providing [LyroHapticsController] throughout the UI hierarchy.
 */
val LocalLyroHaptics = compositionLocalOf<LyroHapticsController?> { null }

/**
 * Remembers a [LyroHapticsController] scoped to the current composable, falling back
 * to [LocalLyroHaptics] if available.
 */
@Composable
fun rememberLyroHaptics(isEnabled: Boolean = true): LyroHapticsController {
    val provided = LocalLyroHaptics.current
    if (provided != null) return provided

    val view = LocalView.current
    return remember(view, isEnabled) {
        LyroHapticsController(view) { isEnabled }
    }
}
