package com.lyro.app.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*
import com.lyro.app.core.haptics.rememberLyroHaptics
import com.lyro.app.ui.components.SleepTimerDial

/**
 * Premium Sleep Timer Modal Bottom Sheet.
 * Features a circular rotary timer dial inspired by Lyro's Wheel Player seek wheel,
 * center-hub quick presets (15 → 30 → 45 → 60 → 15), 5-120 minute custom range in 5m steps,
 * and distinct non-destructive dismissal vs explicit "Turn Off Timer".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerDialog(
    currentMinutesLeft: Int? = null,
    onSetTimer: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = rememberLyroHaptics()

    // Initialize dial selection: use active timer (rounded to nearest 5m) or default 15
    var selectedMinutes by remember(currentMinutesLeft) {
        mutableIntStateOf(
            if (currentMinutesLeft != null && currentMinutesLeft > 0) {
                SleepTimerUtils.roundToNearestStep(currentMinutesLeft)
            } else {
                SleepTimerUtils.DEFAULT_MINUTES
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LyroSurfaceElevated,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        windowInsets = WindowInsets(0, 0, 0, 0),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(LyroTextMuted.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Title & Remaining Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Sleep Timer",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = LyroTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (currentMinutesLeft != null && currentMinutesLeft > 0) {
                        Text(
                            text = "$currentMinutesLeft min remaining",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = LyroAccent
                        )
                    } else {
                        Text(
                            text = "Turn off playback automatically",
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp,
                            color = LyroTextSecondary
                        )
                    }
                }

                if (currentMinutesLeft != null && currentMinutesLeft > 0) {
                    LyroBadge(
                        text = "Active",
                        backgroundColor = LyroAccentMuted,
                        textColor = LyroAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Circular Timer Dial
            SleepTimerDial(
                selectedMinutes = selectedMinutes,
                onMinutesChanged = { selectedMinutes = it },
                onCenterQuickPreset = {
                    val next = SleepTimerUtils.getNextPreset(selectedMinutes)
                    selectedMinutes = next
                    onSetTimer(next)
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Primary Action: Set Timer
            LyroButton(
                onClick = {
                    haptics.click()
                    onSetTimer(selectedMinutes)
                    onDismiss()
                },
                backgroundColor = LyroAccent,
                contentColor = Color.Black,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Set Timer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary Action: Turn Off Timer (if active) or Cancel
            if (currentMinutesLeft != null && currentMinutesLeft > 0) {
                TextButton(
                    onClick = {
                        haptics.click()
                        onSetTimer(null)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Turn Off Timer",
                        color = Color(0xFFEF5350),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            } else {
                TextButton(
                    onClick = {
                        haptics.click()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cancel",
                        color = LyroTextSecondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
