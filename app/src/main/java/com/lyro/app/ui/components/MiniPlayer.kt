package com.lyro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.data.model.Song
import com.lyro.app.service.PlaybackManager

/**
 * Self-contained MiniPlayer container.
 * Collects high-frequency playback position and duration locally so the root
 * MainActivity doesn't recompose 4+ times/second during active playback.
 */
@Composable
fun MiniPlayer(
    playbackManager: PlaybackManager,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTrack by playbackManager.currentTrack.collectAsState()
    val currentSong by playbackManager.currentSong.collectAsState()
    if (currentTrack == null && currentSong == null) return

    val isPlaying by playbackManager.isPlaying.collectAsState()
    val currentPosition by playbackManager.currentPosition.collectAsState()
    val duration by playbackManager.duration.collectAsState()

    MiniPlayerContent(
        track = currentTrack,
        song = currentSong,
        isPlaying = isPlaying,
        currentPosition = currentPosition,
        duration = duration,
        onPlayPauseClick = { playbackManager.togglePlayPause() },
        onNextClick = { playbackManager.skipNext() },
        onExpandClick = onExpandClick,
        modifier = modifier
    )
}

@Composable
fun MiniPlayerContent(
    track: PlayableTrack? = null,
    song: Song? = null,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (track == null && song == null) return

    val haptics = com.lyro.app.core.haptics.rememberLyroHaptics()
    val shape = RoundedCornerShape(12.dp)
    val progress = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    val title = track?.title ?: song?.title ?: ""
    val artist = track?.artist ?: song?.artist ?: ""

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(LyroSurfaceElevated)
                .clickable {
                    haptics.selection()
                    onExpandClick()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork Thumbnail
                if (track != null) {
                    SongArtworkThumbnail(track = track, size = 48.dp)
                } else if (song != null) {
                    SongArtworkThumbnail(song = song, size = 48.dp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title & Artist
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = LyroTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = artist,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = LyroTextSecondary
                    )
                }

                // Audio visualizer bars (accent color, minimal)
                AudioVisualizerBar(
                    isPlaying = isPlaying,
                    barColor = LyroAccent,
                    maxHeight = 14.dp,
                    barWidth = 2.dp
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Play / Pause button
                LyroIconButton(
                    onClick = {
                        haptics.click()
                        onPlayPauseClick()
                    },
                    size = 38.dp,
                    backgroundColor = Color.Transparent
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = LyroTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Next button
                LyroIconButton(
                    onClick = {
                        haptics.click()
                        onNextClick()
                    },
                    size = 38.dp,
                    backgroundColor = Color.Transparent
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = LyroTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Thin accent progress line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(LyroDivider)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progress)
                        .background(LyroAccent)
                )
            }
        }
    }
}

