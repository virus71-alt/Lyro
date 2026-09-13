package com.lyro.app.ui.nowplaying

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*

@Composable
fun SleepTimerDialog(
    onSetTimer: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            NeoButton(
                onClick = {
                    onSetTimer(null)
                    onDismiss()
                },
                backgroundColor = NeoWhite
            ) {
                Text("CANCEL TIMER", fontWeight = FontWeight.Black, color = NeoBlack)
            }
        },
        title = {
            Text("SLEEP TIMER", fontWeight = FontWeight.Black, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60).forEach { mins ->
                    NeoButton(
                        onClick = {
                            onSetTimer(mins)
                            onDismiss()
                        },
                        backgroundColor = NeoCyberYellow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "$mins MINUTES",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = NeoBlack
                        )
                    }
                }
            }
        },
        containerColor = NeoBgLight,
        shape = RoundedCornerShape(14.dp)
    )
}
