package com.lyro.app.ui.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.model.Song
import com.lyro.app.data.repository.SortOrder
import com.lyro.app.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    viewModel: SongsViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val songs by viewModel.songs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    var selectedSongForMenu by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBgLight)
    ) {
        // Storage Permission Banner if not granted
        if (!hasPermission) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                NeoCard(
                    backgroundColor = NeoCyberYellow,
                    shadowOffset = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "DEVICE ACCESS REQUIRED",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = NeoBlack
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Lyro needs storage access to scan and play your offline audio files.",
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = NeoBlack
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        NeoButton(
                            onClick = onRequestPermission,
                            backgroundColor = NeoHotPink
                        ) {
                            Text(
                                text = "ALLOW ACCESS",
                                color = NeoWhite,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Neo-Brutalist Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .padding(end = 3.dp, bottom = 3.dp)
        ) {
            // Shadow
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 3.dp, y = 3.dp)
                    .background(NeoBlack, RoundedCornerShape(10.dp))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NeoWhite, RoundedCornerShape(10.dp))
                    .border(2.dp, NeoBlack, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = NeoBlack,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search tracks, artists, albums...",
                            color = NeoBlack.copy(alpha = 0.45f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        singleLine = true,
                        cursorBrush = SolidColor(NeoBlack),
                        textStyle = TextStyle(
                            color = NeoBlack,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.onSearchQueryChanged("") },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = NeoBlack,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Filter & Sort Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeoChip(
                text = "ALL TUNES (${songs.size})",
                selected = !favoritesOnly,
                onClick = { viewModel.setFavoritesFilter(false) },
                activeColor = NeoAcidGreen
            )

            NeoChip(
                text = "FAVORITES ♥",
                selected = favoritesOnly,
                onClick = { viewModel.setFavoritesFilter(true) },
                activeColor = NeoHotPink
            )

            NeoChip(
                text = if (sortOrder == SortOrder.TITLE) "SORT: A-Z" else "A-Z",
                selected = sortOrder == SortOrder.TITLE,
                onClick = { viewModel.onSortOrderChanged(SortOrder.TITLE) },
                activeColor = NeoCyberYellow
            )

            NeoChip(
                text = if (sortOrder == SortOrder.DATE_ADDED) "SORT: NEWEST" else "NEWEST",
                selected = sortOrder == SortOrder.DATE_ADDED,
                onClick = { viewModel.onSortOrderChanged(SortOrder.DATE_ADDED) },
                activeColor = NeoCyan
            )

            NeoChip(
                text = if (sortOrder == SortOrder.DURATION) "SORT: LENGTH" else "LENGTH",
                selected = sortOrder == SortOrder.DURATION,
                onClick = { viewModel.onSortOrderChanged(SortOrder.DURATION) },
                activeColor = NeoLavender
            )
        }

        // Songs List or Empty State
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                NeoCard(
                    backgroundColor = NeoLavender,
                    shadowOffset = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "NO TUNES FOUND",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = NeoBlack
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No results matching \"$searchQuery\"" else "Add audio files to your device or rescan!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = NeoBlack.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        NeoButton(
                            onClick = { viewModel.loadSongs() },
                            backgroundColor = NeoAcidGreen
                        ) {
                            Text(
                                text = "RESCAN STORAGE",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = NeoBlack
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp)
            ) {
                items(
                    items = songs,
                    key = { it.id }
                ) { song ->
                    SongListItem(
                        song = song,
                        isCurrentSong = currentSong?.id == song.id,
                        isPlaying = isPlaying && currentSong?.id == song.id,
                        onClick = { viewModel.playSong(song) },
                        onFavoriteToggle = { viewModel.toggleFavorite(song) },
                        onMoreClick = { selectedSongForMenu = song }
                    )
                }
            }
        }
    }

    // Playlist Add Modal Sheet
    selectedSongForMenu?.let { song ->
        ModalBottomSheet(
            onDismissRequest = { selectedSongForMenu = null },
            containerColor = NeoBgLight,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "ADD TO PLAYLIST",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = NeoBlack
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${song.title} - ${song.artist}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = NeoBlack.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                NeoButton(
                    onClick = {
                        showCreatePlaylistDialog = true
                    },
                    backgroundColor = NeoAcidGreen,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = NeoBlack)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CREATE NEW PLAYLIST",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = NeoBlack
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (playlists.isEmpty()) {
                    Text(
                        text = "No playlists created yet.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NeoBlack.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    playlists.forEach { pl ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            NeoButton(
                                onClick = {
                                    viewModel.addSongToPlaylist(pl.id, song.id)
                                    selectedSongForMenu = null
                                },
                                backgroundColor = NeoWhite,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = pl.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = NeoBlack
                                    )
                                    NeoBadge(
                                        text = "${pl.songCount} TUNES",
                                        backgroundColor = NeoCyberYellow
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog for creating playlist
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            confirmButton = {
                NeoButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName, (0..6).random())
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                    backgroundColor = NeoAcidGreen
                ) {
                    Text("SAVE", fontWeight = FontWeight.Black, color = NeoBlack)
                }
            },
            dismissButton = {
                NeoButton(
                    onClick = { showCreatePlaylistDialog = false },
                    backgroundColor = NeoWhite
                ) {
                    Text("CANCEL", fontWeight = FontWeight.Bold, color = NeoBlack)
                }
            },
            title = {
                Text("NEW PLAYLIST", fontWeight = FontWeight.Black, fontSize = 16.sp)
            },
            text = {
                Column {
                    Text("Enter playlist title:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NeoWhite, RoundedCornerShape(8.dp))
                            .border(2.dp, NeoBlack, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        BasicTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
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
