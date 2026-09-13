package com.lyro.app.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.media3.common.Player
import com.lyro.app.core.designsystem.*
import com.lyro.app.ui.components.SongArtworkThumbnail

/**
 * Modern, clean, artwork-centric Minimal Now Playing Screen inspired by contemporary streaming players.
 * Large centered artwork, clean text hierarchy, thin slider, prominent circular play button, and minimal actions.
 */
import com.lyro.app.core.haptics.rememberLyroHaptics

@Composable
fun MinimalPlayerScreen(
    viewModel: NowPlayingViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberLyroHaptics()
    val currentSong by viewModel.currentSong.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isFavorite by viewModel.isCurrentTrackFavorite.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val queueTracks by viewModel.queueTracks.collectAsState()
    val isLoadingMoreQueue by viewModel.isLoadingMoreQueue.collectAsState()
    val queueContinuationError by viewModel.queueContinuationError.collectAsState()
    val sleepTimerMinutesLeft by viewModel.sleepTimerMinutesLeft.collectAsState()
    val downloadStatus by viewModel.currentDownloadStatus.collectAsState()
    val isRadioActive by viewModel.isRadioActive.collectAsState()
    val currentRadioSession by viewModel.currentRadioSession.collectAsState()

    val motionProgress = LocalPlayerMotionProgress.current
    val headerAlpha = (1f - motionProgress * 0.7f).coerceIn(0f, 1f)
    val artScale = lerp(1f, 0.82f, motionProgress)

    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var seekSliderPosition by remember { mutableFloatStateOf(0f) }

    val effectiveProgress = if (isUserSeeking) {
        seekSliderPosition
    } else {
        if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            LyroSurfaceElevated,
            LyroBackground,
            LyroBackground
        )
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = LyroBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
        // 1. Top Header Row: Collapse, Centered "Now Playing", More
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = headerAlpha },
            horizontalArrangement = Arrangement.SpaceBetween,
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
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    tint = LyroTextPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }

            if (isRadioActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = LyroAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Lyro Radio",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LyroAccent
                    )
                }
            } else {
                Text(
                    text = "Now Playing",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = LyroTextSecondary
                )
            }

            IconButton(
                onClick = {
                    haptics.click()
                    showQueueSheet = true
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = LyroTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Large Centered Artwork (Artwork-first visual focus with restrained scale motion)
        val artworkShape = RoundedCornerShape(16.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = artScale
                    scaleY = artScale
                }
                .clip(artworkShape)
                .background(LyroSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (currentTrack != null) {
                SongArtworkThumbnail(
                    track = currentTrack!!,
                    modifier = Modifier.fillMaxSize(),
                    size = 320.dp,
                    highRes = true
                )
            } else if (currentSong != null) {
                SongArtworkThumbnail(
                    song = currentSong!!,
                    modifier = Modifier.fillMaxSize(),
                    size = 320.dp,
                    highRes = true
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = LyroTextMuted,
                    modifier = Modifier.size(72.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 3. Song Title & Artist (Clean typography hierarchy, no cards, no fake badges)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = currentTrack?.title ?: currentSong?.title ?: "No Track Playing",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = LyroTextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = currentTrack?.artist ?: currentSong?.artist ?: "Select a song from your library or explore",
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = LyroTextSecondary
            )

            if (sleepTimerMinutesLeft != null) {
                Spacer(modifier = Modifier.height(8.dp))
                LyroBadge(
                    text = "Sleep Timer: ${sleepTimerMinutesLeft}m",
                    backgroundColor = LyroAccentMuted,
                    textColor = LyroAccent
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Thin Seek Bar & Monospace Timestamps
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
                    haptics.strongClick()
                    val targetMs = (seekSliderPosition * duration).toLong()
                    viewModel.seekTo(targetMs)
                },
                colors = SliderDefaults.colors(
                    thumbColor = LyroAccent,
                    activeTrackColor = LyroAccent,
                    inactiveTrackColor = LyroDivider
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
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = LyroTextSecondary
                )
                Text(
                    text = NowPlayingUtils.formatTime(duration),
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = LyroTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Main Transport Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            IconButton(
                onClick = {
                    haptics.selection()
                    viewModel.toggleShuffle()
                },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) LyroAccent else LyroTextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Previous
            IconButton(
                onClick = {
                    haptics.click()
                    viewModel.skipPrevious()
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = LyroTextPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Prominent Play/Pause Circular Accent Button
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(LyroAccent)
                    .clickable {
                        haptics.click()
                        viewModel.togglePlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Next
            IconButton(
                onClick = {
                    haptics.click()
                    viewModel.skipNext()
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = LyroTextPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Repeat Mode
            val repeatTint = when (repeatMode) {
                Player.REPEAT_MODE_OFF -> LyroTextSecondary
                else -> LyroAccent
            }
            IconButton(
                onClick = {
                    haptics.selection()
                    viewModel.cycleRepeatMode()
                },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = repeatTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 6. Bottom Actions: Favorite, Download, Sleep Timer, Queue
        PlayerBottomActions(
            currentTrack = currentTrack,
            isFavorite = isFavorite,
            onFavoriteClick = { viewModel.toggleFavorite(it) },
            currentSong = currentSong,
            sleepTimerMinutesLeft = sleepTimerMinutesLeft,
            downloadStatus = downloadStatus,
            onDownloadClick = { viewModel.downloadCurrentTrack() },
            onSleepTimerClick = { showSleepTimerDialog = true },
            onQueueClick = { showQueueSheet = true }
        )
    }
}

    // Queue Bottom Sheet
    if (showQueueSheet) {
        QueueBottomSheet(
            queue = queueTracks,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            onTrackClick = {
                viewModel.playQueueTrack(it)
                showQueueSheet = false
            },
            onItemClick = { index ->
                viewModel.playQueueItem(index)
                showQueueSheet = false
            },
            isLoadingMore = isLoadingMoreQueue,
            loadMoreError = queueContinuationError,
            onRetryLoadMore = { viewModel.retryQueueExtension() },
            onScrollNearBottom = { viewModel.ensureMoreQueueTracks() },
            isRadioActive = isRadioActive,
            radioSeedTitle = currentRadioSession?.seedTitle,
            onStopRadio = { viewModel.stopRadio() },
            onDismiss = { showQueueSheet = false }
        )
    }

    // Sleep Timer Dialog
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            currentMinutesLeft = sleepTimerMinutesLeft,
            onSetTimer = { viewModel.setSleepTimer(it) },
            onDismiss = { showSleepTimerDialog = false }
        )
    }
}
