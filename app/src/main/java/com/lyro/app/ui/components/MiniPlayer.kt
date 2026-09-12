package com.lyro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.model.Song

@Composable
fun MiniPlayer(
    song: Song?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (song == null) return

    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    val progress = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .padding(end = 4.dp, bottom = 4.dp)
    ) {
        // Hard shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(NeoBlack, shape)
        )

        // Main mini player card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NeoCyberYellow, shape)
                .border(2.5.dp, NeoBlack, shape)
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onExpandClick
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini Artwork
                SongArtworkThumbnail(song = song, size = 44.dp)

                Spacer(modifier = Modifier.width(10.dp))

                // Title & Artist
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = song.title,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = NeoBlack
                    )
                    Text(
                        text = song.artist,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = NeoBlack.copy(alpha = 0.8f)
                    )
                }

                // Audio visualizer bars
                AudioVisualizerBar(
                    isPlaying = isPlaying,
                    barColor = NeoBlack,
                    maxHeight = 16.dp,
                    barWidth = 3.dp
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Play / Pause tactile button
                NeoIconButton(
                    onClick = onPlayPauseClick,
                    backgroundColor = NeoAcidGreen,
                    size = 36.dp,
                    shadowOffset = 2.dp
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = NeoBlack,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Skip next button
                NeoIconButton(
                    onClick = onNextClick,
                    backgroundColor = NeoWhite,
                    size = 36.dp,
                    shadowOffset = 2.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = NeoBlack,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Neo-Brutalist Progress bar at the bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(NeoBlack.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progress)
                        .background(NeoBlack)
                )
            }
        }
    }
}
