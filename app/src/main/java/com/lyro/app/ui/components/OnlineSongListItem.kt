package com.lyro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.Color
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.download.DownloadStatus
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.PlayableTrack

@Composable
fun OnlineSongListItem(
    track: OnlineTrack,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isResolving: Boolean,
    downloadStatus: DownloadStatus,
    onDownloadClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(10.dp)
    val cardBg = if (isCurrentTrack) Color(0xFFD6F8FF) else NeoWhite

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .padding(end = 3.dp, bottom = 3.dp)
    ) {
        // Drop shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(NeoBlack, shape)
        )

        // Track item row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBg, shape)
                .border(2.dp, NeoBlack, shape)
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork Thumbnail
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(NeoLavender, RoundedCornerShape(8.dp))
                    .border(2.dp, NeoBlack, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!track.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = "YT",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = NeoBlack
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Artist
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = track.title,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = NeoBlack,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (isCurrentTrack) {
                        if (isResolving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = NeoBlack,
                                strokeWidth = 2.dp
                            )
                        } else {
                            AudioVisualizerBar(
                                isPlaying = isPlaying,
                                barColor = NeoBlack,
                                maxHeight = 14.dp,
                                barWidth = 2.5.dp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = track.artist,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = NeoBlack.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (track.durationMs > 0) {
                        NeoBadge(
                            text = track.formattedDuration(),
                            backgroundColor = NeoGrayLight,
                            textColor = NeoBlack
                        )
                    }

                    NeoBadge(
                        text = "ONLINE",
                        backgroundColor = NeoAcidGreen,
                        textColor = NeoBlack
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1-Click Download Button
                val downloadBg = when (downloadStatus) {
                    is DownloadStatus.Completed -> NeoAcidGreen
                    is DownloadStatus.Downloading -> NeoCyberYellow
                    is DownloadStatus.Failed -> NeoHotPink
                    is DownloadStatus.Idle -> NeoWhite
                }

                Box(
                    modifier = Modifier.padding(end = 2.dp, bottom = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .offset(x = 2.dp, y = 2.dp)
                            .background(NeoBlack, RoundedCornerShape(6.dp))
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(downloadBg, RoundedCornerShape(6.dp))
                            .border(1.5.dp, NeoBlack, RoundedCornerShape(6.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(
                                enabled = downloadStatus !is DownloadStatus.Downloading && downloadStatus !is DownloadStatus.Completed,
                                onClick = onDownloadClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (downloadStatus) {
                            is DownloadStatus.Downloading -> {
                                CircularProgressIndicator(
                                    progress = { downloadStatus.progress },
                                    modifier = Modifier.size(16.dp),
                                    color = NeoBlack,
                                    strokeWidth = 2.dp,
                                    trackColor = NeoBlack.copy(alpha = 0.2f)
                                )
                            }
                            is DownloadStatus.Completed -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Downloaded",
                                    tint = NeoBlack,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            is DownloadStatus.Failed -> {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry Download",
                                    tint = NeoWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            is DownloadStatus.Idle -> {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download to Device",
                                    tint = NeoBlack,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Play Icon Button
                Box(
                    modifier = Modifier.padding(end = 2.dp, bottom = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .offset(x = 2.dp, y = 2.dp)
                            .background(NeoBlack, RoundedCornerShape(6.dp))
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(if (isCurrentTrack) NeoAcidGreen else NeoWhite, RoundedCornerShape(6.dp))
                            .border(1.5.dp, NeoBlack, RoundedCornerShape(6.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = NeoBlack,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
