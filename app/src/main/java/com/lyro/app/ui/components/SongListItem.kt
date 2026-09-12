package com.lyro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
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
fun SongListItem(
    song: Song,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(10.dp)
    val cardBg = if (isCurrentSong) NeoAcidGreen.copy(alpha = 0.25f) else NeoWhite

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

        // Song item row
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
            SongArtworkThumbnail(song = song, size = 48.dp)

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
                        text = song.title,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = NeoBlack,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (isCurrentSong) {
                        AudioVisualizerBar(
                            isPlaying = isPlaying,
                            barColor = NeoAcidGreen,
                            maxHeight = 14.dp,
                            barWidth = 2.5.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = song.artist,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = NeoBlack.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    NeoBadge(
                        text = song.formattedDuration(),
                        backgroundColor = NeoGrayLight,
                        textColor = NeoBlack
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Favorite button
            NeoIconButton(
                onClick = onFavoriteToggle,
                backgroundColor = if (song.isFavorite) NeoHotPink else NeoWhite,
                size = 32.dp,
                shadowOffset = 2.dp
            ) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (song.isFavorite) NeoWhite else NeoBlack,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // More Options
            NeoIconButton(
                onClick = onMoreClick,
                backgroundColor = NeoGrayLight,
                size = 32.dp,
                shadowOffset = 2.dp
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = NeoBlack,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
