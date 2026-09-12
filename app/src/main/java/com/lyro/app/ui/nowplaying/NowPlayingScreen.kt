package com.lyro.app.ui.nowplaying

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.lyro.app.core.designsystem.*
import com.lyro.app.ui.components.CassetteArtwork
import java.util.Locale

@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel,
    onBackClick: () -> Unit,
    onEqualizerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val sleepTimerMinutesLeft by viewModel.sleepTimerMinutesLeft.collectAsState()

    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var seekSliderPosition by remember { mutableFloatStateOf(0f) }

    val effectiveProgress = if (isUserSeeking) {
        seekSliderPosition
    } else {
        if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    }

    fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBgLight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
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
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    tint = NeoBlack,
                    modifier = Modifier.size(26.dp)
                )
            }

            NeoBadge(
                text = "NOW PLAYING",
                backgroundColor = NeoAcidGreen,
                textColor = NeoBlack
            )

            NeoIconButton(
                onClick = onEqualizerClick,
                backgroundColor = NeoLavender,
                size = 40.dp
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Equalizer",
                    tint = NeoBlack,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hero Cassette Tape View
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            CassetteArtwork(
                song = currentSong,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Song Title & Artist Card
        NeoCard(
            backgroundColor = NeoWhite,
            shadowOffset = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentSong?.title ?: "No Song Selected",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = NeoBlack
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentSong?.artist ?: "Choose a track from library",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = NeoBlack.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeoBadge(
                        text = "LOSSLESS",
                        backgroundColor = NeoCyan
                    )
                    NeoBadge(
                        text = currentSong?.formattedSize() ?: "4.2 MB",
                        backgroundColor = NeoGrayLight
                    )
                    if (sleepTimerMinutesLeft != null) {
                        NeoBadge(
                            text = "TIMER: ${sleepTimerMinutesLeft}M",
                            backgroundColor = NeoHotPink,
                            textColor = NeoWhite
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Chunky Neo-Brutalist Seekbar
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Slider(
                value = effectiveProgress,
                onValueChange = {
                    isUserSeeking = true
                    seekSliderPosition = it
                },
                onValueChangeFinished = {
                    isUserSeeking = false
                    val targetMs = (seekSliderPosition * duration).toLong()
                    viewModel.seekTo(targetMs)
                },
                colors = SliderDefaults.colors(
                    thumbColor = NeoBlack,
                    activeTrackColor = NeoAcidGreen,
                    inactiveTrackColor = NeoGrayMedium
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val currentMs = if (isUserSeeking) (seekSliderPosition * duration).toLong() else currentPosition
                Text(
                    text = formatTime(currentMs),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = NeoBlack
                )
                Text(
                    text = formatTime(duration),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = NeoBlack
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main Tactile Transport Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            NeoIconButton(
                onClick = { viewModel.toggleShuffle() },
                backgroundColor = if (isShuffle) NeoCyberYellow else NeoWhite,
                size = 44.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = NeoBlack,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Previous
            NeoIconButton(
                onClick = { viewModel.skipPrevious() },
                backgroundColor = NeoWhite,
                size = 48.dp
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = NeoBlack,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Giant Play/Pause Button
            NeoIconButton(
                onClick = { viewModel.togglePlayPause() },
                backgroundColor = NeoAcidGreen,
                size = 64.dp,
                shadowOffset = 5.dp
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = NeoBlack,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Next
            NeoIconButton(
                onClick = { viewModel.skipNext() },
                backgroundColor = NeoWhite,
                size = 48.dp
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = NeoBlack,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Repeat Mode
            val repeatBg = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> NeoHotPink
                Player.REPEAT_MODE_ALL -> NeoCyberYellow
                else -> NeoWhite
            }
            NeoIconButton(
                onClick = { viewModel.cycleRepeatMode() },
                backgroundColor = repeatBg,
                size = 44.dp
            ) {
                Icon(
                    imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = if (repeatMode == Player.REPEAT_MODE_ONE) NeoWhite else NeoBlack,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Action Bar: Favorite, Sleep Timer, Queue
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Favorite Button
            val isFav = currentSong?.isFavorite == true
            NeoIconButton(
                onClick = { currentSong?.let { viewModel.toggleFavorite(it) } },
                backgroundColor = if (isFav) NeoHotPink else NeoWhite,
                size = 44.dp
            ) {
                Icon(
                    imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFav) NeoWhite else NeoBlack,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Sleep Timer Button
            NeoIconButton(
                onClick = { showSleepTimerDialog = true },
                backgroundColor = if (sleepTimerMinutesLeft != null) NeoHotPink else NeoWhite,
                size = 44.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Sleep Timer",
                    tint = if (sleepTimerMinutesLeft != null) NeoWhite else NeoBlack,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Queue Button
            NeoIconButton(
                onClick = { showQueueSheet = true },
                backgroundColor = NeoCyberYellow,
                size = 44.dp
            ) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = "Queue",
                    tint = NeoBlack,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    // Queue Bottom Sheet
    if (showQueueSheet) {
        QueueBottomSheet(
            queue = queue,
            currentSong = currentSong,
            isPlaying = isPlaying,
            onSongClick = {
                viewModel.playQueueItem(it)
                showQueueSheet = false
            },
            onDismiss = { showQueueSheet = false }
        )
    }

    // Sleep Timer Dialog
    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            confirmButton = {},
            dismissButton = {
                NeoButton(
                    onClick = {
                        viewModel.setSleepTimer(null)
                        showSleepTimerDialog = false
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
                                viewModel.setSleepTimer(mins)
                                showSleepTimerDialog = false
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
}
