package com.lyro.app.ui.settings

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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.preferences.PlayerPreferences
import com.lyro.app.data.preferences.PlayerStyle
import com.lyro.app.ui.songs.SongsViewModel

@Composable
fun SettingsScreen(
    viewModel: SongsViewModel,
    playerPreferences: PlayerPreferences,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentStyle by playerPreferences.playerStyle.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBgLight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeoIconButton(
                onClick = onBackClick,
                backgroundColor = NeoWhite,
                size = 40.dp
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NeoBlack,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = "LYRO SETTINGS",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = NeoBlack
            )

            NeoBadge(
                text = "v1.0",
                backgroundColor = NeoCyberYellow,
                textColor = NeoBlack
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Hero Card (Accurate local + online description)
        NeoCard(
            backgroundColor = NeoAcidGreen,
            shadowOffset = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text(
                    text = "LYRO MUSIC",
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = NeoBlack
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "A bold local + online music player designed with Neo-Brutalist aesthetics.",
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = NeoBlack
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NeoBadge(text = "HYBRID AUDIO", backgroundColor = NeoWhite)
                    NeoBadge(text = "ZERO ADS", backgroundColor = NeoHotPink, textColor = NeoWhite)
                    NeoBadge(text = "HI-FI STREAM", backgroundColor = NeoCyan)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Player Appearance Selection
        Text(
            text = "PLAYER APPEARANCE",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = NeoBlack
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cassette Player Style Option
            PlayerStyleOptionCard(
                title = "CASSETTE PLAYER",
                description = "Original Lyro cassette tape design with animated reels.",
                isSelected = currentStyle == PlayerStyle.CASSETTE,
                onClick = { playerPreferences.setPlayerStyle(PlayerStyle.CASSETTE) }
            )

            // Wheel Player Style Option
            PlayerStyleOptionCard(
                title = "WHEEL PLAYER",
                description = "Circular tactile wheel controls with continuous drag seeking.",
                isSelected = currentStyle == PlayerStyle.WHEEL,
                onClick = { playerPreferences.setPlayerStyle(PlayerStyle.WHEEL) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Library Management
        Text(
            text = "LIBRARY CONTROLS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = NeoBlack
        )

        Spacer(modifier = Modifier.height(8.dp))

        NeoCard(
            backgroundColor = NeoWhite,
            shadowOffset = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Rescan Audio Storage",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = NeoBlack
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Refresh track metadata and index newly downloaded music files.",
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = NeoBlack.copy(alpha = 0.7f)
                        )
                    }

                    NeoIconButton(
                        onClick = { viewModel.loadSongs() },
                        backgroundColor = NeoCyberYellow,
                        size = 38.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = NeoBlack,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Audio Engine Info
        Text(
            text = "AUDIO ENGINE",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = NeoBlack
        )

        Spacer(modifier = Modifier.height(8.dp))

        NeoCard(
            backgroundColor = NeoWhite,
            shadowOffset = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Playback Backend", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    NeoBadge(text = "AndroidX Media3 ExoPlayer", backgroundColor = NeoLavender)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Supported Formats", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "MP3, FLAC, AAC, WAV, OGG", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Headphone Unplug Pause", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    NeoBadge(text = "ENABLED", backgroundColor = NeoAcidGreen)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Privacy Card (Accurate description)
        NeoCard(
            backgroundColor = NeoLavender,
            shadowOffset = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = NeoBlack,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "Private by Design",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = NeoBlack
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Lyro does not collect, track, or sell your listening data. Local audio files stay on your device, and online streaming connects directly to audio providers.",
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = NeoBlack.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerStyleOptionCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val cardBg = if (isSelected) NeoAcidGreen.copy(alpha = 0.15f) else NeoWhite

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 4.dp, bottom = 4.dp)
    ) {
        // Hard drop shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(NeoBlack, shape)
        )

        // Card content
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBg, shape)
                .border(
                    width = if (isSelected) 2.5.dp else 2.dp,
                    color = NeoBlack,
                    shape = shape
                )
                .clip(shape)
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = NeoBlack
                    )
                    if (isSelected) {
                        NeoBadge(
                            text = "ACTIVE",
                            backgroundColor = NeoAcidGreen,
                            textColor = NeoBlack
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = NeoBlack.copy(alpha = 0.75f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Selection Indicator
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        if (isSelected) NeoAcidGreen else NeoWhite,
                        CircleShape
                    )
                    .border(2.dp, NeoBlack, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = NeoBlack,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
