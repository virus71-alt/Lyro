package com.lyro.app.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*
import com.lyro.app.core.haptics.rememberLyroHaptics

@Composable
fun SleepTimerDialog(
    onSetTimer: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = rememberLyroHaptics()
    val shape = RoundedCornerShape(16.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = {
                    haptics.click()
                    onSetTimer(null)
                    onDismiss()
                }
            ) {
                Text("Cancel Timer", color = LyroTextSecondary, fontWeight = FontWeight.Medium)
            }
        },
        title = {
            Text(
                text = "Sleep Timer",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = LyroTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(15, 30, 45, 60).forEach { mins ->
                    val itemShape = RoundedCornerShape(10.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(itemShape)
                            .background(LyroSurface)
                            .clickable {
                                haptics.selection()
                                onSetTimer(mins)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "$mins minutes",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = LyroTextPrimary
                        )
                    }
                }
            }
        },
        containerColor = LyroSurfaceElevated,
        shape = shape
    )
}
