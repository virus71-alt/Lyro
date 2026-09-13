package com.lyro.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*

@Composable
fun NeoBrutalAppBar(
    title: String = "LYRO",
    isOnlineMode: Boolean = false,
    onToggleOnlineMode: () -> Unit = {},
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
        // App Title / Branding with Neo-Brutalist box + Mode Toggle Switcher
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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

            // Neo-Brutalist Online / Offline Switcher Button
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val offsetAnim by animateDpAsState(
                targetValue = if (isPressed) 1.dp else 2.5.dp,
                animationSpec = tween(80),
                label = "mode_btn_offset"
            )
            val shiftAnim by animateDpAsState(
                targetValue = if (isPressed) 1.5.dp else 0.dp,
                animationSpec = tween(80),
                label = "mode_btn_shift"
            )
            val badgeColor by animateColorAsState(
                targetValue = if (isOnlineMode) NeoCyan else NeoCyberYellow,
                animationSpec = tween(150),
                label = "mode_btn_color"
            )

            Box(
                modifier = Modifier.padding(end = 2.5.dp, bottom = 2.5.dp)
            ) {
                // Hard drop shadow
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(x = offsetAnim, y = offsetAnim)
                        .background(NeoBlack, RoundedCornerShape(6.dp))
                )

                // Button Surface
                Row(
                    modifier = Modifier
                        .offset(x = shiftAnim, y = shiftAnim)
                        .background(badgeColor, RoundedCornerShape(6.dp))
                        .border(2.dp, NeoBlack, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onToggleOnlineMode
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(if (isOnlineMode) NeoAcidGreen else NeoBlack, CircleShape)
                            .border(1.dp, NeoBlack, CircleShape)
                    )
                    Text(
                        text = if (isOnlineMode) "ONLINE 🌐" else "OFFLINE ⚡",
                        color = NeoBlack,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Actions: Settings
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
