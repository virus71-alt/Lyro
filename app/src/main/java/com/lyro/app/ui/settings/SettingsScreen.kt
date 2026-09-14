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
import androidx.compose.material.icons.filled.Delete
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

import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import com.lyro.app.core.haptics.rememberLyroHaptics

@Composable
fun SettingsScreen(
    viewModel: SongsViewModel,
    playerPreferences: PlayerPreferences,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberLyroHaptics()
    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val currentStyle by playerPreferences.playerStyle.collectAsState()
    val isHapticsEnabled by playerPreferences.isHapticsEnabled.collectAsState()

    val lyroLinkState by viewModel.lyroLinkState.collectAsState()

    val smartDownloadState by viewModel.smartDownloadState.collectAsState()
    val isWifiOnly by viewModel.smartDownloadPreferences.isWifiOnly.collectAsState()
    val isChargingOnly by viewModel.smartDownloadPreferences.isChargingOnly.collectAsState()
    val hasSeenConsentDialog by viewModel.smartDownloadPreferences.hasSeenConsentDialog.collectAsState()

    var showConsentDialog by remember { mutableStateOf(false) }
    var showDisableDialog by remember { mutableStateOf(false) }
    var showRemoveConfirmDialog by remember { mutableStateOf(false) }

    // Dialog: First-time Consent
    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { showConsentDialog = false },
            containerColor = LyroSurfaceElevated,
            title = {
                Text(
                    text = "Enable Smart Downloads",
                    fontWeight = FontWeight.Bold,
                    color = LyroTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Lyro can automatically save music you may like for offline listening.",
                        color = LyroTextPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "• Keeps a rotating offline collection within your chosen storage limit.\n" +
                               "• Songs rotate gradually over time as your taste evolves.\n" +
                               "• Manual downloads are never auto-deleted.\n" +
                               "• Smart downloads can be cleared at any time in Settings.",
                        color = LyroTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.click()
                        viewModel.markConsentSeen()
                        viewModel.setSmartDownloadsEnabled(true)
                        showConsentDialog = false
                    }
                ) {
                    Text("Turn On", color = LyroAccent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        haptics.click()
                        showConsentDialog = false
                    }
                ) {
                    Text("Cancel", color = LyroTextSecondary)
                }
            }
        )
    }

    // Dialog: Disabling Choice (Keep vs Remove)
    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDialog = false },
            containerColor = LyroSurfaceElevated,
            title = {
                Text(
                    text = "Turn Off Smart Downloads?",
                    fontWeight = FontWeight.Bold,
                    color = LyroTextPrimary
                )
            },
            text = {
                Text(
                    text = "Smart Downloads will stop automatically maintaining offline music. What would you like to do with existing smart downloads?",
                    color = LyroTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            haptics.click()
                            viewModel.setSmartDownloadsEnabled(false)
                            showDisableDialog = false
                        }
                    ) {
                        Text("Keep Songs", color = LyroAccent)
                    }
                    TextButton(
                        onClick = {
                            haptics.click()
                            viewModel.setSmartDownloadsEnabled(false)
                            viewModel.removeSmartDownloads()
                            showDisableDialog = false
                        }
                    ) {
                        Text("Remove Songs", color = Color(0xFFEF5350))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableDialog = false }) {
                    Text("Cancel", color = LyroTextSecondary)
                }
            }
        )
    }

    // Dialog: Remove Smart Downloads Confirmation
    if (showRemoveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirmDialog = false },
            containerColor = LyroSurfaceElevated,
            title = {
                Text(
                    text = "Remove Smart Downloads",
                    fontWeight = FontWeight.Bold,
                    color = LyroTextPrimary
                )
            },
            text = {
                Text(
                    text = "This will delete all automatically downloaded songs. Your manual downloads and local music will remain completely untouched.",
                    color = LyroTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.click()
                        viewModel.removeSmartDownloads()
                        showRemoveConfirmDialog = false
                    }
                ) {
                    Text("Remove", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirmDialog = false }) {
                    Text("Cancel", color = LyroTextSecondary)
                }
            }
        )
    }

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

        // SECTION: Smart Downloads (Lyro Smart Offline Mix)
        SettingsSectionHeader(title = "Smart Downloads")

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(LyroSurfaceElevated)
                .border(1.dp, LyroDivider, cardShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smart Downloads",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = LyroTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Automatically save music you may like for offline listening",
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        color = LyroTextSecondary
                    )
                }

                Switch(
                    checked = smartDownloadState.isEnabled,
                    onCheckedChange = { enable ->
                        haptics.selection()
                        if (enable) {
                            if (!hasSeenConsentDialog) {
                                showConsentDialog = true
                            } else {
                                viewModel.setSmartDownloadsEnabled(true)
                            }
                        } else {
                            showDisableDialog = true
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = LyroAccent,
                        uncheckedThumbColor = LyroTextSecondary,
                        uncheckedTrackColor = LyroSurfaceHighlight
                    )
                )
            }

            if (smartDownloadState.isEnabled) {
                HorizontalDivider(color = LyroDivider, thickness = 0.8.dp)

                // Storage Limit Presets
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Storage limit",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = LyroTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            1L * 1024 * 1024 * 1024 to "1 GB",
                            2L * 1024 * 1024 * 1024 to "2 GB",
                            3L * 1024 * 1024 * 1024 to "3 GB",
                            5L * 1024 * 1024 * 1024 to "5 GB"
                        )
                        presets.forEach { (bytes, label) ->
                            val isSelected = smartDownloadState.limitBytes == bytes
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) LyroAccent else LyroSurfaceHighlight)
                                    .clickable {
                                        if (!isSelected) {
                                            haptics.selection()
                                            viewModel.setSmartDownloadLimit(bytes)
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.Black else LyroTextPrimary
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = LyroDivider, thickness = 0.8.dp)

                // Wi-Fi Only Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wi-Fi only",
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                            color = LyroTextPrimary
                        )
                        Text(
                            text = "Only download when connected to Wi-Fi",
                            fontSize = 12.sp,
                            color = LyroTextSecondary
                        )
                    }
                    Switch(
                        checked = isWifiOnly,
                        onCheckedChange = {
                            haptics.selection()
                            viewModel.setSmartDownloadWifiOnly(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = LyroAccent,
                            uncheckedThumbColor = LyroTextSecondary,
                            uncheckedTrackColor = LyroSurfaceHighlight
                        )
                    )
                }

                HorizontalDivider(color = LyroDivider, thickness = 0.8.dp)

                // Charging Only Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Charging only",
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                            color = LyroTextPrimary
                        )
                        Text(
                            text = "Only download when device is charging",
                            fontSize = 12.sp,
                            color = LyroTextSecondary
                        )
                    }
                    Switch(
                        checked = isChargingOnly,
                        onCheckedChange = {
                            haptics.selection()
                            viewModel.setSmartDownloadChargingOnly(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = LyroAccent,
                            uncheckedThumbColor = LyroTextSecondary,
                            uncheckedTrackColor = LyroSurfaceHighlight
                        )
                    )
                }

                HorizontalDivider(color = LyroDivider, thickness = 0.8.dp)

                // Status Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LyroSurfaceHighlight)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val usedStr = formatStorageBytes(smartDownloadState.usedBytes)
                    val limitStr = formatStorageBytes(smartDownloadState.limitBytes)
                    val progress = if (smartDownloadState.limitBytes > 0) {
                        (smartDownloadState.usedBytes.toFloat() / smartDownloadState.limitBytes.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$usedStr of $limitStr used",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = LyroTextPrimary
                        )
                        Text(
                            text = "${smartDownloadState.trackCount} smart-downloaded songs",
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = LyroAccent
                        )
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = LyroAccent,
                        trackColor = LyroDivider
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (smartDownloadState.isMaintaining) "Updating offline mix..." else "Last updated: ${formatSmartTimestamp(smartDownloadState.lastUpdated)}",
                            fontSize = 11.sp,
                            color = if (smartDownloadState.isMaintaining) LyroAccent else LyroTextMuted
                        )
                        if (smartDownloadState.lastError != null) {
                            Text(
                                text = "Notice",
                                fontSize = 11.sp,
                                color = Color(0xFFEF5350)
                            )
                        }
                    }
                }

                // Actions: Refresh offline mix & Remove smart downloads
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            haptics.click()
                            viewModel.refreshOfflineMix()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = LyroTextPrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LyroDivider)
                    ) {
                        if (smartDownloadState.isMaintaining) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = LyroAccent
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = LyroAccent
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Refresh offline mix",
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.click()
                            showRemoveConfirmDialog = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFEF5350)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LyroDivider)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFEF5350)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Remove smart downloads",
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
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

        // SECTION: Lyro Link
        SettingsSectionHeader(title = "Lyro Link")

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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Lyro Link",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = LyroTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Play your Lyro library on devices connected to the same Wi-Fi.",
                        fontSize = 12.sp,
                        color = LyroTextSecondary,
                        lineHeight = 16.sp
                    )
                }

                Switch(
                    checked = lyroLinkState.enabled,
                    onCheckedChange = { isChecked ->
                        haptics.selection()
                        if (isChecked) {
                            viewModel.startLyroLink()
                        } else {
                            viewModel.stopLyroLink()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = LyroAccent,
                        uncheckedThumbColor = LyroTextSecondary,
                        uncheckedTrackColor = LyroSurfaceHighlight
                    )
                )
            }

            if (lyroLinkState.enabled) {
                HorizontalDivider(color = LyroDivider, thickness = 0.8.dp)

                if (lyroLinkState.running && lyroLinkState.fullAddress != null) {
                    // Address Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(LyroSurfaceHighlight)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Address",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = LyroTextSecondary
                                )
                                Text(
                                    text = lyroLinkState.fullAddress ?: "",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LyroAccent
                                )
                            }

                            Button(
                                onClick = {
                                    haptics.click()
                                    lyroLinkState.fullAddress?.let { addr ->
                                        clipboardManager.setText(AnnotatedString(addr))
                                        Toast.makeText(context, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LyroSurfaceElevated,
                                    contentColor = LyroTextPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        HorizontalDivider(color = LyroDivider, thickness = 0.8.dp)

                        // Pairing Code
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Pairing code",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = LyroTextSecondary
                                )
                                Text(
                                    text = lyroLinkState.pairingCode ?: "------",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 4.sp,
                                    color = LyroTextPrimary
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptics.click()
                                    viewModel.regenerateLinkPairingCode()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Regenerate code",
                                    tint = LyroAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = LyroDivider, thickness = 0.8.dp)

                        // Connected Devices
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = LyroTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Connected devices",
                                    fontSize = 13.sp,
                                    color = LyroTextSecondary
                                )
                            }

                            Text(
                                text = "${lyroLinkState.connectedClients} ${if (lyroLinkState.connectedClients == 1) "device" else "devices"}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (lyroLinkState.connectedClients > 0) LyroAccent else LyroTextPrimary
                            )
                        }
                    }

                    // Stop button
                    OutlinedButton(
                        onClick = {
                            haptics.click()
                            viewModel.stopLyroLink()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                    ) {
                        Text("Stop Lyro Link", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                } else {
                    // Status warning / not running
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2E1A1A))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = lyroLinkState.statusMessage ?: "Wi-Fi connection required",
                            color = Color(0xFFFF8A80),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ensure this phone and your other device are on the same local Wi-Fi network.",
                            color = LyroTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Text(
                    text = "Lyro Link is available only to devices on your local network while enabled.",
                    fontSize = 11.sp,
                    color = LyroTextMuted,
                    lineHeight = 15.sp
                )
            }
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

private fun formatStorageBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        val gb = mb / 1024.0
        String.format(java.util.Locale.US, "%.1f GB", gb)
    } else {
        String.format(java.util.Locale.US, "%.0f MB", mb)
    }
}

private fun formatSmartTimestamp(timestamp: Long): String {
    if (timestamp <= 0) return "Never"
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000L -> "Just now"
        diff < 3600_000L -> "${diff / 60_000L}m ago"
        diff < 24 * 3600_000L -> "Today"
        diff < 48 * 3600_000L -> "Yesterday"
        else -> "${diff / (24 * 3600_000L)}d ago"
    }
}
