package com.lyro.app.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.model.Playlist
import com.lyro.app.ui.components.NeoEmptyState
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
            .background(NeoBgLight)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Create Playlist Row & Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "YOUR MIXTAPES",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = NeoBlack
            )

            NeoButton(
                onClick = { showCreateDialog = true },
                backgroundColor = NeoAcidGreen
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New",
                    tint = NeoBlack,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "NEW MIXTAPE",
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    color = NeoBlack
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Featured Card: Liked Songs / Favorites Tape
        NeoCard(
            backgroundColor = NeoHotPink,
            shadowOffset = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onLikedSongsClick() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                            .background(NeoWhite, RoundedCornerShape(10.dp))
                            .border(2.dp, NeoBlack, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Favorites",
                            tint = NeoHotPink,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "FAVORITE TUNES",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = NeoWhite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$likedCount Liked Tracks",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = NeoWhite.copy(alpha = 0.9f)
                        )
                    }
                }

                NeoIconButton(
                    onClick = onLikedSongsClick,
                    backgroundColor = NeoWhite,
                    size = 40.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = NeoBlack,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (playlists.isEmpty()) {
            NeoEmptyState(
                icon = Icons.Default.LibraryMusic,
                iconBackgroundColor = NeoCyan,
                title = "NO CUSTOM MIXTAPES",
                description = "You haven't created any mixtapes yet. Organize your favorite songs into custom collections!",
                primaryButtonText = "+ CREATE NEW MIXTAPE",
                primaryButtonColor = NeoAcidGreen,
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
                    val bg = NeoAccentPalette[playlist.colorIndex % NeoAccentPalette.size]

                    NeoCard(
                        backgroundColor = bg,
                        shadowOffset = 3.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaylistClick(playlist) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(NeoWhite, RoundedCornerShape(8.dp))
                                    .border(2.dp, NeoBlack, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    tint = NeoBlack,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = playlist.name,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = NeoBlack
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            NeoBadge(
                                text = "${playlist.songCount} TUNES",
                                backgroundColor = NeoWhite,
                                textColor = NeoBlack
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            confirmButton = {
                NeoButton(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            viewModel.createPlaylist(playlistName, (0..6).random())
                            playlistName = ""
                            showCreateDialog = false
                        }
                    },
                    backgroundColor = NeoAcidGreen
                ) {
                    Text("CREATE", fontWeight = FontWeight.Black, color = NeoBlack)
                }
            },
            dismissButton = {
                NeoButton(
                    onClick = { showCreateDialog = false },
                    backgroundColor = NeoWhite
                ) {
                    Text("CANCEL", fontWeight = FontWeight.Bold, color = NeoBlack)
                }
            },
            title = {
                Text("NEW MIXTAPE", fontWeight = FontWeight.Black, fontSize = 16.sp)
            },
            text = {
                Column {
                    Text("Give your playlist a cool brutalist name:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NeoWhite, RoundedCornerShape(8.dp))
                            .border(2.dp, NeoBlack, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        BasicTextField(
                            value = playlistName,
                            onValueChange = { playlistName = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            },
            containerColor = NeoBgLight,
            shape = RoundedCornerShape(12.dp)
        )
    }
}
