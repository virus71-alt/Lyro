package com.lyro.app.ui.nowplaying

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.lyro.app.core.designsystem.LyroBackground
import com.lyro.app.core.haptics.rememberLyroHaptics
import kotlinx.coroutines.launch

val LocalPlayerMotionProgress = compositionLocalOf { 0f }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandablePlayerContainer(
    sheetState: PlayerSheetState,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val haptics = rememberLyroHaptics()
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHeight = constraints.maxHeight.toFloat()
        LaunchedEffect(screenHeight) {
            sheetState.collapseDistance = screenHeight
            if (sheetState.currentValue == PlayerSheetValue.COLLAPSED && sheetState.dragOffsetY == 0f) {
                sheetState.snapTo(screenHeight)
            }
        }

        val nestedScrollConnection = remember(sheetState, coroutineScope, onCollapse) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val deltaY = available.y
                    // If dragging UP and sheet is partially dragged down, pull sheet back UP towards 0
                    if (deltaY < 0f && sheetState.dragOffsetY > 0f) {
                        val newOffset = (sheetState.dragOffsetY + deltaY).coerceAtLeast(0f)
                        val consumedY = newOffset - sheetState.dragOffsetY
                        coroutineScope.launch { sheetState.snapTo(newOffset) }
                        sheetState.checkThresholdHaptic(newOffset, haptics)
                        return Offset(0f, consumedY)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    val deltaY = available.y
                    // If dragging DOWN and child is at scroll position 0 (cannot scroll up),
                    // drag the sheet DOWN!
                    if (deltaY > 0f && source == NestedScrollSource.Drag) {
                        val newOffset = (sheetState.dragOffsetY + deltaY).coerceAtMost(sheetState.collapseDistance)
                        val consumedY = newOffset - sheetState.dragOffsetY
                        coroutineScope.launch { sheetState.snapTo(newOffset) }
                        sheetState.checkThresholdHaptic(newOffset, haptics)
                        return Offset(0f, consumedY)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (sheetState.dragOffsetY > 0f) {
                        sheetState.handleRelease(available.y, haptics, onCollapse)
                        return available
                    }
                    return Velocity.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (available.y > 0f && sheetState.dragOffsetY > 0f && !sheetState.animatableOffset.isRunning) {
                        sheetState.handleRelease(available.y, haptics, onCollapse)
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        val progress = sheetState.collapseProgress
        val topCornerRadius = (22f * progress).dp
        val sheetShape = RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius)

        // 1. Scrim behind the player surface during collapse (reveals underlying destination)
        if (progress > 0f && progress < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f * (1f - progress)))
            )
        }

        // 2. Real-time draggable player surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .graphicsLayer {
                    translationY = sheetState.dragOffsetY
                    if (progress > 0f) {
                        shape = sheetShape
                        clip = true
                        shadowElevation = 16.dp.toPx()
                    }
                }
                .background(LyroBackground)
        ) {
            CompositionLocalProvider(
                LocalOverscrollConfiguration provides null,
                LocalPlayerMotionProgress provides progress
            ) {
                content()
            }
        }
    }
}
