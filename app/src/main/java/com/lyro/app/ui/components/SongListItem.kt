package com.lyro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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

@Composable
fun SongListItem(
    song: Song,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rowShape = RoundedCornerShape(10.dp)
    val rowBg = if (isCurrentSong) LyroSurfaceElevated.copy(alpha = 0.7f) else Color.Transparent

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
        SongArtworkThumbnail(song = song, size = 52.dp)

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
                    text = song.title,
                    fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentSong) LyroAccent else LyroTextPrimary,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (isCurrentSong) {
                    AudioVisualizerBar(
                        isPlaying = isPlaying,
                        barColor = LyroAccent,
                        maxHeight = 12.dp,
                        barWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "${song.artist} • ${song.formattedDuration()}",
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = LyroTextSecondary
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Favorite Button
        IconButton(
            onClick = onFavoriteToggle,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (song.isFavorite) LyroAccent else LyroTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }

        // More Options
        IconButton(
            onClick = onMoreClick,
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
