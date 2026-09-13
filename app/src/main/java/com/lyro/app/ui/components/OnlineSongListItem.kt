package com.lyro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.download.DownloadStatus
import com.lyro.app.data.model.OnlineTrack

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
    val rowShape = RoundedCornerShape(10.dp)
    val rowBg = if (isCurrentTrack) LyroSurfaceElevated.copy(alpha = 0.7f) else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(rowShape)
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork Thumbnail
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(LyroSurfaceElevated),
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
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = LyroTextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title & Artist / Duration
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = track.title,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentTrack) LyroAccent else LyroTextPrimary,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (isCurrentTrack) {
                    if (isResolving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = LyroAccent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        AudioVisualizerBar(
                            isPlaying = isPlaying,
                            barColor = LyroAccent,
                            maxHeight = 12.dp,
                            barWidth = 2.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            val subtitleText = if (track.durationMs > 0) {
                "${track.artist} • ${track.formattedDuration()}"
            } else {
                track.artist
            }

            Text(
                text = subtitleText,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = LyroTextSecondary
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 1-Click Download Button
        IconButton(
            onClick = onDownloadClick,
            enabled = downloadStatus !is DownloadStatus.Downloading && downloadStatus !is DownloadStatus.Completed,
            modifier = Modifier.size(36.dp)
        ) {
            when (downloadStatus) {
                is DownloadStatus.Downloading -> {
                    CircularProgressIndicator(
                        progress = { downloadStatus.progress },
                        modifier = Modifier.size(18.dp),
                        color = LyroAccent,
                        strokeWidth = 2.dp,
                        trackColor = LyroSurfaceHighlight
                    )
                }
                is DownloadStatus.Completed -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Downloaded",
                        tint = LyroAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                is DownloadStatus.Failed -> {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry Download",
                        tint = LyroError,
                        modifier = Modifier.size(20.dp)
                    )
                }
                is DownloadStatus.Idle -> {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download to Device",
                        tint = LyroTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
