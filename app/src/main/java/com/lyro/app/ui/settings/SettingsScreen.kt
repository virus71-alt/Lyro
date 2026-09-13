package com.lyro.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.R
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.preferences.PlayerPreferences
import com.lyro.app.data.preferences.PlayerStyle
import com.lyro.app.ui.songs.SongsViewModel

import com.lyro.app.core.haptics.rememberLyroHaptics

@Composable
fun SettingsScreen(
    viewModel: SongsViewModel,
    playerPreferences: PlayerPreferences,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberLyroHaptics()
    val currentStyle by playerPreferences.playerStyle.collectAsState()
    val isHapticsEnabled by playerPreferences.isHapticsEnabled.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = LyroBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LyroBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    haptics.click()
                    onBackClick()
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = LyroTextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = LyroTextPrimary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION: Player Appearance
        SettingsSectionHeader(title = "Player Appearance")

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PlayerStyleOption(
                title = "Minimal Player (Default)",
                description = "Modern artwork-first player with clean spacing, thin seek bar, and refined controls.",
                isSelected = currentStyle == PlayerStyle.MINIMAL,
                onClick = {
                    if (currentStyle != PlayerStyle.MINIMAL) {
                        haptics.selection()
                        playerPreferences.setPlayerStyle(PlayerStyle.MINIMAL)
                    }
                }
            )

            PlayerStyleOption(
                title = "Cassette Player",
                description = "Classic retro cassette deck with animated spools and clean info.",
                isSelected = currentStyle == PlayerStyle.CASSETTE,
                onClick = {
                    if (currentStyle != PlayerStyle.CASSETTE) {
                        haptics.selection()
                        playerPreferences.setPlayerStyle(PlayerStyle.CASSETTE)
                    }
                }
            )

            PlayerStyleOption(
                title = "Wheel Player",
                description = "Circular tactile wheel controls with continuous drag seeking.",
                isSelected = currentStyle == PlayerStyle.WHEEL,
                onClick = {
                    if (currentStyle != PlayerStyle.WHEEL) {
                        haptics.selection()
                        playerPreferences.setPlayerStyle(PlayerStyle.WHEEL)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // SECTION: Feedback & Haptics
        SettingsSectionHeader(title = "Feedback")

        Spacer(modifier = Modifier.height(10.dp))

        val cardShape = RoundedCornerShape(12.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(LyroSurfaceElevated)
                .border(1.dp, LyroDivider, cardShape)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Haptic feedback",
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = LyroTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Tactile clicks for controls, navigation, and wheel detents",
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    color = LyroTextSecondary
                )
            }

            Switch(
                checked = isHapticsEnabled,
                onCheckedChange = { enabled ->
                    haptics.selection()
                    playerPreferences.setHapticsEnabled(enabled)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = LyroAccent,
                    uncheckedThumbColor = LyroTextSecondary,
                    uncheckedTrackColor = LyroSurfaceHighlight
                )
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // SECTION: Library
        SettingsSectionHeader(title = "Library")

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(LyroSurfaceElevated)
                .border(1.dp, LyroDivider, cardShape)
                .clickable {
                    haptics.click()
                    viewModel.loadSongs()
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Rescan Audio Storage",
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = LyroTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Refresh metadata and index new downloads and files",
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    color = LyroTextSecondary
                )
            }

            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Rescan",
                tint = LyroAccent,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // SECTION: Audio Engine
        SettingsSectionHeader(title = "Audio Engine")

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(LyroSurfaceElevated)
                .border(1.dp, LyroDivider, cardShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsInfoRow(label = "Backend", value = "AndroidX Media3 ExoPlayer")
            HorizontalDivider(color = LyroDivider, thickness = 0.8.dp)
            SettingsInfoRow(label = "Supported Formats", value = "MP3, FLAC, AAC, WAV, OGG")
            HorizontalDivider(color = LyroDivider, thickness = 0.8.dp)
            SettingsInfoRow(label = "Pause on Headphone Disconnect", value = "Enabled")
        }

        Spacer(modifier = Modifier.height(28.dp))

        // SECTION: About & Privacy
        SettingsSectionHeader(title = "About")

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(LyroSurfaceElevated)
                .border(1.dp, LyroDivider, cardShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Lyro",
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Column {
                    Text(
                        text = "Lyro",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = LyroTextPrimary
                    )
                    Text(
                        text = "Minimal music player",
                        fontSize = 12.sp,
                        color = LyroTextSecondary
                    )
                }
            }
            HorizontalDivider(color = LyroDivider, thickness = 0.8.dp)

            SettingsInfoRow(label = "App Version", value = "1.0.0")
            HorizontalDivider(color = LyroDivider, thickness = 0.8.dp)

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = LyroAccent,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Private by Design",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = LyroTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Lyro does not track or sell listening data. Local audio files remain strictly on your device.",
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        color = LyroTextSecondary,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = LyroAccent,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = LyroTextPrimary
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = LyroTextSecondary
        )
    }
}

@Composable
private fun PlayerStyleOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val cardBg = if (isSelected) LyroSurfaceHighlight else LyroSurfaceElevated
    val borderColor = if (isSelected) LyroAccent else LyroDivider

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardBg)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 15.sp,
                color = if (isSelected) LyroAccent else LyroTextPrimary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                color = LyroTextSecondary,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    if (isSelected) LyroAccent else Color.Transparent,
                    CircleShape
                )
                .border(
                    width = 1.5.dp,
                    color = if (isSelected) LyroAccent else LyroTextMuted,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
