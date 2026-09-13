package com.lyro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.data.model.Song

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.lyro.app.core.haptics.rememberLyroHaptics

/**
 * Reusable, compact, modern music track row inspired by minimal streaming apps.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    title: String,
    artist: String,
    durationText: String?,
    artworkContent: @Composable () -> Unit,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val haptics = rememberLyroHaptics()
    val rowShape = RoundedCornerShape(10.dp)
    val rowBg = if (isCurrent) LyroSurfaceElevated.copy(alpha = 0.6f) else Color.Transparent

    val hasMore = onMoreClick != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(rowBg)
            .combinedClickable(
                onClick = {
                    haptics.click()
                    onClick()
                },
                onLongClick = if (hasMore) {
                    {
                        haptics.longPress()
                        onMoreClick?.invoke()
                    }
                } else null
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(LyroSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            artworkContent()
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title & Artist / Metadata
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrent) LyroAccent else LyroTextPrimary,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (isCurrent) {
                    AudioVisualizerBar(
                        isPlaying = isPlaying,
                        barColor = LyroAccent,
                        maxHeight = 12.dp,
                        barWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            val meta = if (!durationText.isNullOrBlank()) "$artist • $durationText" else artist
            Text(
                text = meta,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = LyroTextSecondary
            )
        }

        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(6.dp))
            trailingContent()
        } else if (onMoreClick != null) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = {
                    haptics.click()
                    onMoreClick()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = LyroTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    SongRow(
        title = song.title,
        artist = song.artist,
        durationText = song.formattedDuration(),
        artworkContent = {
            SongArtworkThumbnail(song = song, size = 56.dp)
        },
        isCurrent = isCurrent,
        isPlaying = isPlaying,
        onClick = onClick,
        modifier = modifier,
        onMoreClick = onMoreClick,
        trailingContent = trailingContent
    )
}

@Composable
fun SongRow(
    track: OnlineTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    SongRow(
        title = track.title,
        artist = track.artist,
        durationText = if (track.durationMs > 0) track.formattedDuration() else null,
        artworkContent = {
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
        },
        isCurrent = isCurrent,
        isPlaying = isPlaying,
        onClick = onClick,
        modifier = modifier,
        onMoreClick = onMoreClick,
        trailingContent = trailingContent
    )
}
