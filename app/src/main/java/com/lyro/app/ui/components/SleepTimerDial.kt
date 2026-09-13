package com.lyro.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*
import com.lyro.app.core.haptics.rememberLyroHaptics
import com.lyro.app.ui.nowplaying.SleepTimerUtils
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Premium circular Sleep Timer dial inspired by Lyro's Wheel Player.
 * Maps discrete 5-minute detents across 5 to 120 minutes with smooth angular delta tracking,
 * wrap at ±180°, center dead-zone hit separation, and a center quick-preset hub.
 */
@Composable
fun SleepTimerDial(
    selectedMinutes: Int,
    onMinutesChanged: (Int) -> Unit,
    onCenterQuickPreset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberLyroHaptics()
    val density = LocalDensity.current

    val dialDiameter = 210.dp
    val hubDiameter = 90.dp
    val hubRadiusPx = with(density) { (hubDiameter / 2).toPx() }

    var wheelCenter by remember { mutableStateOf(Offset.Zero) }
    var lastAngle by remember { mutableFloatStateOf(0f) }
    var accumulatedAngle by remember { mutableFloatStateOf(0f) }
    var isDraggingDial by remember { mutableStateOf(false) }

    // Internal state updates synchronously during active drag
    var currentDragMinutes by remember { mutableIntStateOf(selectedMinutes) }

    LaunchedEffect(selectedMinutes) {
        currentDragMinutes = selectedMinutes
    }

    val currentMinutesState by rememberUpdatedState(currentDragMinutes)
    val onMinutesChangedState by rememberUpdatedState(onMinutesChanged)

    val fraction = ((currentDragMinutes - SleepTimerUtils.MIN_MINUTES).toFloat() /
            (SleepTimerUtils.MAX_MINUTES - SleepTimerUtils.MIN_MINUTES)).coerceIn(0f, 1f)

    // Smooth 120Hz-feel animation for the active arc progress
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "dialFraction"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(dialDiameter)
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
                this.contentDescription = "Sleep timer dial, $currentDragMinutes minutes"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { targetFraction ->
                    val mins = SleepTimerUtils.MIN_MINUTES +
                            (targetFraction * (SleepTimerUtils.MAX_MINUTES - SleepTimerUtils.MIN_MINUTES)).toInt()
                    val stepped = SleepTimerUtils.roundToNearestStep(mins)
                    currentDragMinutes = stepped
                    onMinutesChangedState(stepped)
                    true
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        accumulatedAngle = 0f
                        val dx = offset.x - wheelCenter.x
                        val dy = offset.y - wheelCenter.y
                        val dist = sqrt(dx * dx + dy * dy)
                        // Ignore touch if initiated inside center dead zone
                        if (dist < hubRadiusPx) {
                            isDraggingDial = false
                            return@detectDragGestures
                        }
                        isDraggingDial = true
                        lastAngle = (atan2(dy, dx) * (180f / PI.toFloat()) + 360f) % 360f
                    },
                    onDragEnd = {
                        isDraggingDial = false
                        accumulatedAngle = 0f
                    },
                    onDragCancel = {
                        isDraggingDial = false
                        accumulatedAngle = 0f
                    },
                    onDrag = { change, _ ->
                        if (!isDraggingDial) return@detectDragGestures
                        change.consume()
                        val dx = change.position.x - wheelCenter.x
                        val dy = change.position.y - wheelCenter.y
                        val currentAngle = (atan2(dy, dx) * (180f / PI.toFloat()) + 360f) % 360f
                        val delta = SleepTimerUtils.normalizeAngleDelta(currentAngle, lastAngle)
                        accumulatedAngle += delta

                        val (steps, remainder) = SleepTimerUtils.calculateStepDelta(
                            accumulatedAngle,
                            SleepTimerUtils.DEGREES_PER_STEP
                        )

                        if (steps != 0) {
                            val activeMins = currentMinutesState
                            val newMinutes = (activeMins + steps * SleepTimerUtils.STEP_MINUTES)
                                .coerceIn(SleepTimerUtils.MIN_MINUTES, SleepTimerUtils.MAX_MINUTES)
                            if (newMinutes != activeMins) {
                                accumulatedAngle = remainder
                                currentDragMinutes = newMinutes
                                haptics.tick()
                                onMinutesChangedState(newMinutes)
                            } else {
                                accumulatedAngle = 0f
                            }
                        }
                        lastAngle = currentAngle
                    }
                )
            }
    ) {
        // Dial Canvas: Track, Accent Arc, and 24 Tactile Detent Dots
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
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
            val sweepAngle = 360f * animatedFraction
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

            // 24 Tactile Dots around the circumference (15° steps)
            val dotTrackRadius = radius - strokeWidth - 8.dp.toPx()
            for (i in 0 until 24) {
                val angleDeg = i * 15f - 90f
                val angleRad = angleDeg * (PI / 180f).toFloat()
                val dotCenter = Offset(
                    center.x + dotTrackRadius * cos(angleRad),
                    center.y + dotTrackRadius * sin(angleRad)
                )
                val isPassed = (i * 15f) <= sweepAngle
                val isMajor = (i % 6 == 0) // Major detent points
                val dotRadius = if (isMajor) 3.5.dp.toPx() else 2.2.dp.toPx()

                drawCircle(
                    color = if (isPassed) LyroAccent else LyroTextMuted.copy(alpha = 0.35f),
                    radius = dotRadius,
                    center = dotCenter
                )
            }
        }

        // Center Quick-Preset Tactile Hub (Tappable shortcut: 15 → 30 → 45 → 60 → 15)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(hubDiameter)
                .clip(CircleShape)
                .background(LyroSurface)
                .border(1.dp, LyroDivider, CircleShape)
                .clickable {
                    haptics.click()
                    onCenterQuickPreset()
                }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$currentDragMinutes",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = LyroTextPrimary
                )
                Text(
                    text = "MINUTES",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = LyroTextSecondary
                )
            }
        }
    }
}
