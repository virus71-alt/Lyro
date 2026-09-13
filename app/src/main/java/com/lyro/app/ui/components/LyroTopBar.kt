package com.lyro.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*

@Composable
fun LyroTopBar(
    title: String = "LYRO",
    isOnlineMode: Boolean = false,
    onToggleOnlineMode: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(LyroBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Title & Clean Mode Selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = LyroTextPrimary
            )

            // Minimal Rounded Pill Mode Selector (Local vs Online)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(LyroSurfaceElevated)
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val localBg by animateColorAsState(
                    targetValue = if (!isOnlineMode) LyroSurfaceHighlight else Color.Transparent,
                    animationSpec = tween(180),
                    label = "localTabBg"
                )
                val onlineBg by animateColorAsState(
                    targetValue = if (isOnlineMode) LyroSurfaceHighlight else Color.Transparent,
                    animationSpec = tween(180),
                    label = "onlineTabBg"
                )

                // Local Tab Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(localBg)
                        .clickable(onClick = { if (isOnlineMode) onToggleOnlineMode() })
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Local",
                        fontSize = 12.sp,
                        fontWeight = if (!isOnlineMode) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (!isOnlineMode) LyroTextPrimary else LyroTextMuted
                    )
                }

                // Online Tab Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(onlineBg)
                        .clickable(onClick = { if (!isOnlineMode) onToggleOnlineMode() })
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isOnlineMode) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(LyroAccent, CircleShape)
                            )
                        }
                        Text(
                            text = "Online",
                            fontSize = 12.sp,
                            fontWeight = if (isOnlineMode) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isOnlineMode) LyroTextPrimary else LyroTextMuted
                        )
                    }
                }
            }
        }

        // Settings Icon Button
        LyroIconButton(
            onClick = onSettingsClick,
            size = 38.dp,
            backgroundColor = LyroSurfaceElevated
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = LyroTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
