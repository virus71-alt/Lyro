package com.lyro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*

@Composable
fun NeoBrutalAppBar(
    title: String = "LYRO",
    onEqualizerClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeoBgLight)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Title / Branding with Neo-Brutalist box
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(NeoAcidGreen, RoundedCornerShape(6.dp))
                    .border(2.dp, NeoBlack, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = title.uppercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.sp,
                    color = NeoBlack
                )
            }

            NeoBadge(
                text = "OFFLINE",
                backgroundColor = NeoCyberYellow,
                textColor = NeoBlack
            )
        }

        // Actions
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeoIconButton(
                onClick = onEqualizerClick,
                backgroundColor = NeoLavender,
                size = 38.dp,
                shadowOffset = 2.dp
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Equalizer",
                    tint = NeoBlack,
                    modifier = Modifier.size(20.dp)
                )
            }

            NeoIconButton(
                onClick = onSettingsClick,
                backgroundColor = NeoCyan,
                size = 38.dp,
                shadowOffset = 2.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = NeoBlack,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
