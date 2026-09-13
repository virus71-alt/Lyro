package com.lyro.app.ui.nowplaying

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.download.DownloadStatus
import com.lyro.app.data.model.Song

@Composable
fun PlayerBottomActions(
    currentSong: Song?,
    sleepTimerMinutesLeft: Int?,
    downloadStatus: DownloadStatus = DownloadStatus.Idle,
    onFavoriteClick: (Song) -> Unit,
    onDownloadClick: () -> Unit = {},
    onSleepTimerClick: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Favorite Button
        val isFav = currentSong?.isFavorite == true
        IconButton(
            onClick = { currentSong?.let { onFavoriteClick(it) } },
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFav) LyroAccent else LyroTextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Download Button (for online track downloads)
        val isDownloaded = downloadStatus is DownloadStatus.Completed
        val isDownloading = downloadStatus is DownloadStatus.Downloading
        val isFailed = downloadStatus is DownloadStatus.Failed

        IconButton(
            onClick = {
                if (isDownloaded) {
                    Toast.makeText(context, "Song already saved to local storage!", Toast.LENGTH_SHORT).show()
                } else if (!isDownloading) {
                    onDownloadClick()
                }
            },
            modifier = Modifier.size(44.dp)
        ) {
            when {
                isDownloading -> {
                    val progress = (downloadStatus as DownloadStatus.Downloading).progress
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(20.dp),
                        color = LyroAccent,
                        strokeWidth = 2.dp,
                        trackColor = LyroSurfaceHighlight
                    )
                }
                isDownloaded -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Downloaded",
                        tint = LyroAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                isFailed -> {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry Download",
                        tint = LyroError,
                        modifier = Modifier.size(22.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download to Device",
                        tint = LyroTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Sleep Timer Button
        IconButton(
            onClick = onSleepTimerClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = "Sleep Timer",
                tint = if (sleepTimerMinutesLeft != null) LyroAccent else LyroTextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Queue Button
        IconButton(
            onClick = onQueueClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = "Queue",
                tint = LyroTextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
