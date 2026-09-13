package com.lyro.app.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.lyro.app.ui.components.AudioVisualizerBar
import com.lyro.app.ui.components.SongArtworkThumbnail

import com.lyro.app.core.haptics.rememberLyroHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onItemClick: ((Int) -> Unit)? = null,
    isRadioActive: Boolean = false,
    radioSeedTitle: String? = null,
    onStopRadio: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val haptics = rememberLyroHaptics()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LyroSurfaceElevated,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(LyroTextMuted.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Playing Queue",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LyroTextPrimary
                    )
                    Text(
                        text = "${queue.size} tracks",
                        fontSize = 12.sp,
                        color = LyroTextSecondary
                    )
                }

                IconButton(
                    onClick = {
                        haptics.click()
                        onDismiss()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = LyroTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isRadioActive) {
                Surface(
                    color = LyroAccent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LyroAccent.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = LyroAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Lyro Radio",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LyroAccent
                                )
                                val subtitle = if (!radioSeedTitle.isNullOrBlank()) "Based on: $radioSeedTitle" else "Endless personalized queue"
                                Text(
                                    text = subtitle,
                                    fontSize = 11.sp,
                                    color = LyroTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (onStopRadio != null) {
                            TextButton(
                                onClick = {
                                    haptics.click()
                                    onStopRadio()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Stop Radio",
                                    color = LyroTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                itemsIndexed(
                    items = queue,
                    key = { index, song -> "${song.id}_$index" },
                    contentType = { _, _ -> "queue_item" }
                ) { index, song ->
                    val isCurrent = song.id == currentSong?.id
                    val shape = RoundedCornerShape(10.dp)
                    val bg = if (isCurrent) LyroSurfaceHighlight else Color.Transparent

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(shape)
                            .background(bg)
                            .clickable {
                                haptics.click()
                                if (onItemClick != null) {
                                    onItemClick(index)
                                } else {
                                    onSongClick(song)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format("%02d", index + 1),
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            color = if (isCurrent) LyroAccent else LyroTextMuted,
                            modifier = Modifier.width(24.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        SongArtworkThumbnail(song = song, size = 42.dp)

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isCurrent) LyroAccent else LyroTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = song.artist,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = LyroTextSecondary
                            )
                        }

                        if (isCurrent) {
                            Spacer(modifier = Modifier.width(8.dp))
                            AudioVisualizerBar(
                                isPlaying = isPlaying,
                                barColor = LyroAccent,
                                maxHeight = 12.dp,
                                barWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}
