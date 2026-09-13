package com.lyro.app.ui.nowplaying

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.model.Song

@Composable
fun PlayerBottomActions(
    currentSong: Song?,
    sleepTimerMinutesLeft: Int?,
    onFavoriteClick: (Song) -> Unit,
    onSleepTimerClick: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Favorite Button
        val isFav = currentSong?.isFavorite == true
        NeoIconButton(
            onClick = { currentSong?.let { onFavoriteClick(it) } },
            backgroundColor = if (isFav) NeoHotPink else NeoWhite,
            size = 44.dp
        ) {
            Icon(
                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFav) NeoWhite else NeoBlack,
                modifier = Modifier.size(22.dp)
            )
        }

        // Sleep Timer Button
        NeoIconButton(
            onClick = onSleepTimerClick,
            backgroundColor = if (sleepTimerMinutesLeft != null) NeoHotPink else NeoWhite,
            size = 44.dp
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = "Sleep Timer",
                tint = if (sleepTimerMinutesLeft != null) NeoWhite else NeoBlack,
                modifier = Modifier.size(22.dp)
            )
        }

        // Queue Button
        NeoIconButton(
            onClick = onQueueClick,
            backgroundColor = NeoCyberYellow,
            size = 44.dp
        ) {
            Icon(
                imageVector = Icons.Default.QueueMusic,
                contentDescription = "Queue",
                tint = NeoBlack,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
