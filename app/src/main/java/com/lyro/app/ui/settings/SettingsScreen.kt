package com.lyro.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*
import com.lyro.app.ui.songs.SongsViewModel

@Composable
fun SettingsScreen(
    viewModel: SongsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    imageVector = Icons.Default.ArrowBack,
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

        // Hero Card
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
                    text = "High-performance offline audio player designed with bold Neo-Brutalist aesthetics.",
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = NeoBlack
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NeoBadge(text = "100% OFFLINE", backgroundColor = NeoWhite)
                    NeoBadge(text = "ZERO ADS", backgroundColor = NeoHotPink, textColor = NeoWhite)
                    NeoBadge(text = "HI-FI AUDIO", backgroundColor = NeoCyan)
                }
            }
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

        // Privacy Card
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
                        text = "100% Private & Local",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = NeoBlack
                    )
                    Text(
                        text = "Lyro does not collect, transmit, or analyze any of your personal audio files or listening history.",
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = NeoBlack.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
