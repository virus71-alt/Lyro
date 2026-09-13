package com.lyro.app.ui.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

        Spacer(modifier = Modifier.height(12.dp))

        // Hero Artwork: Real Album Artwork with Neo-Brutalist Frame
        Box(
            modifier = Modifier.padding(end = 5.dp, bottom = 5.dp)
        ) {
            // Drop shadow
            Box(
                modifier = Modifier
                    .size(175.dp)
                    .offset(x = 5.dp, y = 5.dp)
                    .background(NeoBlack, RoundedCornerShape(16.dp))
            )

            if (currentSong != null) {
                SongArtworkThumbnail(
                    song = currentSong!!,
                    modifier = Modifier
                        .size(175.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    size = 175.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(175.dp)
                        .background(NeoLavender, RoundedCornerShape(16.dp))
                        .border(3.dp, NeoBlack, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "LYRO",
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        color = NeoBlack
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Song Title & Artist (No fake LOSSLESS or file size badges)
        Column(
            modifier = Modifier.fillMaxWidth(),
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
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currentSong?.artist ?: "Select a song to start",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = NeoBlack.copy(alpha = 0.7f)
            )

            if (sleepTimerMinutesLeft != null) {
                Spacer(modifier = Modifier.height(6.dp))
                NeoBadge(
                    text = "TIMER: ${sleepTimerMinutesLeft}M",
                    backgroundColor = NeoHotPink,
                    textColor = NeoWhite
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
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = if (isDraggingWheel) NeoHotPink else NeoBlack
            )

            if (isDraggingWheel) {
                NeoBadge(
                    text = "ROTATING TO SEEK",
                    backgroundColor = NeoCyberYellow,
                    textColor = NeoBlack
                )
            }

            Text(
                text = NowPlayingUtils.formatTime(duration),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = NeoBlack
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive Tactile Wheel
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(bottom = 6.dp, end = 6.dp)
        ) {
            // Wheel Drop Shadow
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .offset(x = 5.dp, y = 5.dp)
                    .background(NeoBlack, CircleShape)
            )

            // Main Tactile Wheel Surface
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(220.dp)
                    .background(NeoWhite, CircleShape)
                    .border(3.5.dp, NeoBlack, CircleShape)
                    .clip(CircleShape)
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

                                // Calculate angular delta and normalize to [-180, 180] to prevent wraparound jump
                                var delta = currentAngle - lastAngle
                                if (delta > 180f) {
                                    delta -= 360f
                                } else if (delta < -180f) {
                                    delta += 360f
                                }

                                // Map angular delta to progress change (1 full circle = 100% track length)
                                val progressDelta = delta / 360f
                                previewProgress = (previewProgress + progressDelta).coerceIn(0f, 1f)
                                lastAngle = currentAngle
                            }
                        )
                    }
            ) {
                // Wheel Canvas: Track, Acid Green Progress Arc, and Tactile Tick Dots
                Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                    val diameter = size.minDimension
                    val radius = diameter / 2f
                    val strokeWidth = 14.dp.toPx()
                    val arcSize = Size(diameter - strokeWidth, diameter - strokeWidth)
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                    // Background Track
                    drawArc(
                        color = NeoGrayLight,
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
                            color = NeoAcidGreen,
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // 12 Tactile Dots around the circumference
                    val dotTrackRadius = radius - strokeWidth - 10.dp.toPx()
                    for (i in 0 until 12) {
                        val angleDeg = i * 30f - 90f
                        val angleRad = angleDeg * (PI / 180f).toFloat()
                        val dotCenter = Offset(
                            center.x + dotTrackRadius * cos(angleRad),
                            center.y + dotTrackRadius * sin(angleRad)
                        )
                        val isPassed = (i * 30f) <= (sweepAngle)
                        drawCircle(
                            color = if (isPassed) NeoBlack else NeoGrayMedium,
                            radius = 3.dp.toPx(),
                            center = dotCenter
                        )
                    }
                }

                // Center Tactile Hub
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .background(NeoWhite, CircleShape)
                        .border(3.dp, NeoBlack, CircleShape)
                        .clip(CircleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(if (isPlaying) NeoAcidGreen else NeoBlack, CircleShape)
                            .border(2.dp, NeoBlack, CircleShape)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Transport Controls
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

            // Large Play/Pause Button
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

        Spacer(modifier = Modifier.height(14.dp))

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
