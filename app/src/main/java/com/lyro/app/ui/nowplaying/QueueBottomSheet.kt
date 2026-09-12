package com.lyro.app.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.model.Song
import com.lyro.app.ui.components.AudioVisualizerBar
import com.lyro.app.ui.components.SongArtworkThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NeoBgLight,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 30.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "PLAYING QUEUE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = NeoBlack
                    )
                    NeoBadge(
                        text = "${queue.size} TUNES",
                        backgroundColor = NeoCyberYellow
                    )
                }

                NeoIconButton(
                    onClick = onDismiss,
                    backgroundColor = NeoWhite,
                    size = 32.dp,
                    shadowOffset = 2.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = NeoBlack,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                itemsIndexed(
                    items = queue,
                    key = { index, song -> "${song.id}_$index" }
                ) { index, song ->
                    val isCurrent = song.id == currentSong?.id
                    val shape = RoundedCornerShape(8.dp)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .padding(end = 2.dp, bottom = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .offset(x = 2.dp, y = 2.dp)
                                .background(NeoBlack, shape)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isCurrent) NeoAcidGreen else NeoWhite, shape)
                                .border(2.dp, NeoBlack, shape)
                                .clickable { onSongClick(song) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format("%02d", index + 1),
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                color = NeoBlack
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            SongArtworkThumbnail(song = song, size = 36.dp)

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = NeoBlack
                                )
                                Text(
                                    text = song.artist,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = NeoBlack.copy(alpha = 0.7f)
                                )
                            }

                            if (isCurrent) {
                                AudioVisualizerBar(
                                    isPlaying = isPlaying,
                                    barColor = NeoBlack,
                                    maxHeight = 14.dp,
                                    barWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = song.formattedDuration(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoBlack.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
