package com.lyro.app.ui.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.lyro.app.core.designsystem.*
import com.lyro.app.ui.components.SongArtworkThumbnail
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WheelPlayerScreen(
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
    val downloadStatus by viewModel.currentDownloadStatus.collectAsState()

    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    // Wheel Drag & Seek state
    var isDraggingWheel by remember { mutableStateOf(false) }
    var previewProgress by remember { mutableFloatStateOf(0f) }
    var lastAngle by remember { mutableFloatStateOf(0f) }
    var wheelCenter by remember { mutableStateOf(Offset.Zero) }

    val currentProgress = if (duration > 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val displayProgress = if (isDraggingWheel) previewProgress else currentProgress
    val displayPositionMs = if (isDraggingWheel) (previewProgress * duration).toLong() else currentPosition

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LyroBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    tint = LyroTextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = "Now Playing",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = LyroTextSecondary
            )

            // Balancer spacer matching collapse button size
            Spacer(modifier = Modifier.size(40.dp))
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Hero Artwork: Album Artwork with sleek minimal rounded corners
        val artShape = RoundedCornerShape(16.dp)
        Box(
            modifier = Modifier
                .size(175.dp)
                .clip(artShape)
                .background(LyroSurfaceElevated)
                .border(1.dp, LyroDivider, artShape)
        ) {
            if (currentSong != null) {
                SongArtworkThumbnail(
                    song = currentSong!!,
                    modifier = Modifier.fillMaxSize(),
                    size = 175.dp
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = LyroTextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Clean Song Title & Artist
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentSong?.title ?: "No Track Playing",
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = LyroTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentSong?.artist ?: "Select a song to start",
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
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

        Spacer(modifier = Modifier.height(12.dp))

        // Time Display Row with Seek Indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = NowPlayingUtils.formatTime(displayPositionMs),
                fontWeight = if (isDraggingWheel) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = if (isDraggingWheel) LyroAccent else LyroTextSecondary
            )

            if (isDraggingWheel) {
                LyroBadge(
                    text = "Rotating to seek",
                    backgroundColor = LyroAccentMuted,
                    textColor = LyroAccent
                )
            }

            Text(
                text = NowPlayingUtils.formatTime(duration),
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = LyroTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive Minimalist Charcoal Wheel
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(LyroSurfaceElevated)
                .border(1.dp, LyroDivider, CircleShape)
                .onGloballyPositioned { coordinates ->
                    wheelCenter = Offset(
                        coordinates.size.width / 2f,
                        coordinates.size.height / 2f
                    )
                }
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(displayProgress, 0f..1f)
                    setProgress { target ->
                        viewModel.seekTo((target * duration).toLong())
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDraggingWheel = true
                            previewProgress = currentProgress
                            val dx = offset.x - wheelCenter.x
                            val dy = offset.y - wheelCenter.y
                            lastAngle = (atan2(dy, dx) * (180f / PI.toFloat()) + 360f) % 360f
                        },
                        onDragEnd = {
                            isDraggingWheel = false
                            val targetMs = (previewProgress * duration).toLong()
                            viewModel.seekTo(targetMs)
                        },
                        onDragCancel = {
                            isDraggingWheel = false
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val dx = change.position.x - wheelCenter.x
                            val dy = change.position.y - wheelCenter.y
                            val currentAngle = (atan2(dy, dx) * (180f / PI.toFloat()) + 360f) % 360f

                            var delta = currentAngle - lastAngle
                            if (delta > 180f) {
                                delta -= 360f
                            } else if (delta < -180f) {
                                delta += 360f
                            }

                            val progressDelta = delta / 360f
                            previewProgress = (previewProgress + progressDelta).coerceIn(0f, 1f)
                            lastAngle = currentAngle
                        }
                    )
                }
        ) {
            // Wheel Canvas: Track, Accent Progress Arc, and Tactile Dots
            Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                val diameter = size.minDimension
                val radius = diameter / 2f
                val strokeWidth = 10.dp.toPx()
                val arcSize = Size(diameter - strokeWidth, diameter - strokeWidth)
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                // Background Track
                drawArc(
                    color = LyroSurfaceHighlight,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )

                // Active Progress Arc (From 12 o'clock = -90 degrees)
                val sweepAngle = 360f * displayProgress
                if (sweepAngle > 0f) {
                    drawArc(
                        color = LyroAccent,
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // 12 Tactile Dots around the circumference
                val dotTrackRadius = radius - strokeWidth - 8.dp.toPx()
                for (i in 0 until 12) {
                    val angleDeg = i * 30f - 90f
                    val angleRad = angleDeg * (PI / 180f).toFloat()
                    val dotCenter = Offset(
                        center.x + dotTrackRadius * cos(angleRad),
                        center.y + dotTrackRadius * sin(angleRad)
                    )
                    val isPassed = (i * 30f) <= sweepAngle
                    drawCircle(
                        color = if (isPassed) LyroAccent else LyroTextMuted.copy(alpha = 0.3f),
                        radius = 2.5.dp.toPx(),
                        center = dotCenter
                    )
                }
            }

            // Center Tactile Hub
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(LyroSurface)
                    .border(1.dp, LyroDivider, CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) LyroAccent else LyroSurfaceHighlight)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Main Transport Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            IconButton(
                onClick = { viewModel.toggleShuffle() },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) LyroAccent else LyroTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Previous
            IconButton(
                onClick = { viewModel.skipPrevious() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = LyroTextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Large Play/Pause Circular Accent Button
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(LyroAccent)
                    .clickable { viewModel.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(34.dp)
                )
            }

            // Next
            IconButton(
                onClick = { viewModel.skipNext() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = LyroTextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Repeat Mode
            val repeatTint = when (repeatMode) {
                Player.REPEAT_MODE_OFF -> LyroTextSecondary
                else -> LyroAccent
            }
            IconButton(
                onClick = { viewModel.cycleRepeatMode() },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = repeatTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Bottom Actions: Favorite, Download, Sleep Timer, Queue
        PlayerBottomActions(
            currentSong = currentSong,
            sleepTimerMinutesLeft = sleepTimerMinutesLeft,
            downloadStatus = downloadStatus,
            onFavoriteClick = { viewModel.toggleFavorite(it) },
            onDownloadClick = { viewModel.downloadCurrentTrack() },
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
