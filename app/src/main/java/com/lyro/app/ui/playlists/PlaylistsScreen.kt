package com.lyro.app.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.model.Playlist
import com.lyro.app.ui.components.LyroEmptyState
import com.lyro.app.ui.songs.SongsViewModel

@Composable
fun PlaylistsScreen(
    viewModel: SongsViewModel,
    onPlaylistClick: (Playlist) -> Unit,
    onLikedSongsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playlists by viewModel.playlists.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val likedCount = remember(songs) { songs.count { it.isFavorite } }

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LyroBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Create Playlist Row & Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your Library",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = LyroTextPrimary
            )

            LyroButton(
                onClick = { showCreateDialog = true },
                backgroundColor = LyroSurfaceElevated,
                contentColor = LyroTextPrimary,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New",
                    tint = LyroAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "New Mixtape",
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = LyroTextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Featured Hero Card: Liked Songs / Favorites
        val likedCardShape = RoundedCornerShape(16.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(likedCardShape)
                .background(LyroSurfaceElevated)
                .border(1.dp, LyroDivider, likedCardShape)
                .clickable { onLikedSongsClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(LyroAccentMuted, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorites",
                        tint = LyroAccent,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column {
                    Text(
                        text = "Liked Songs",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = LyroTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$likedCount tracks",
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = LyroTextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(LyroAccent, CircleShape)
                    .clickable {
                        val favs = songs.filter { it.isFavorite }
                        if (favs.isNotEmpty()) {
                            viewModel.playSong(favs.first(), favs)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (playlists.isEmpty()) {
            LyroEmptyState(
                icon = Icons.Default.LibraryMusic,
                title = "No Custom Mixtapes",
                description = "You haven't created any mixtapes yet. Organize your favorite tracks into custom collections.",
                primaryButtonText = "Create New Mixtape",
                onPrimaryButtonClick = { showCreateDialog = true }
            )
        } else {
            // Playlists Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    val cardShape = RoundedCornerShape(14.dp)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(cardShape)
                            .background(LyroSurfaceElevated)
                            .border(1.dp, LyroDivider, cardShape)
                            .clickable { onPlaylistClick(playlist) }
                            .padding(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(LyroSurfaceHighlight, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LibraryMusic,
                                contentDescription = null,
                                tint = LyroAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = playlist.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = LyroTextPrimary
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = "${playlist.songCount} tracks",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = LyroTextSecondary
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            viewModel.createPlaylist(playlistName, (0..6).random())
                            playlistName = ""
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Create", color = LyroAccent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = LyroTextSecondary)
                }
            },
            title = {
                Text("New Mixtape", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = LyroTextPrimary)
            },
            text = {
                Column {
                    Text("Enter mixtape name:", fontSize = 13.sp, color = LyroTextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LyroSurface, RoundedCornerShape(10.dp))
                            .border(1.dp, LyroDivider, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        BasicTextField(
                            value = playlistName,
                            onValueChange = { playlistName = it },
                            singleLine = true,
                            cursorBrush = SolidColor(LyroAccent),
                            textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = LyroTextPrimary)
                        )
                    }
                }
            },
            containerColor = LyroSurfaceElevated,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
