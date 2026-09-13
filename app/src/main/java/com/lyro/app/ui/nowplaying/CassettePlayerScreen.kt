package com.lyro.app.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.lyro.app.core.designsystem.*
import com.lyro.app.ui.components.CassetteArtwork

@Composable
fun CassettePlayerScreen(
    viewModel: NowPlayingViewModel,
    onBackClick: () -> Unit,
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

            // Balancer spacer matching collapse button size
            Spacer(modifier = Modifier.size(40.dp))
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

        // Song Title & Artist Card (Without fake LOSSLESS or file size badges)
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

                if (sleepTimerMinutesLeft != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    NeoBadge(
                        text = "TIMER: ${sleepTimerMinutesLeft}M",
                        backgroundColor = NeoHotPink,
                        textColor = NeoWhite
                    )
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
                    text = NowPlayingUtils.formatTime(currentMs),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = NeoBlack
                )
                Text(
                    text = NowPlayingUtils.formatTime(duration),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
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
        PlayerBottomActions(
            currentSong = currentSong,
            sleepTimerMinutesLeft = sleepTimerMinutesLeft,
            onFavoriteClick = { viewModel.toggleFavorite(it) },
            onSleepTimerClick = { showSleepTimerDialog = true },
            onQueueClick = { showQueueSheet = true }
        )
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
        SleepTimerDialog(
            onSetTimer = { viewModel.setSleepTimer(it) },
            onDismiss = { showSleepTimerDialog = false }
        )
    }
}
