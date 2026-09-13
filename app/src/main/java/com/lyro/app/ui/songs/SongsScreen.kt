package com.lyro.app.ui.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.download.DownloadStatus
import com.lyro.app.data.model.Song
import com.lyro.app.data.repository.SortOrder
import com.lyro.app.ui.components.LyroEmptyState
import com.lyro.app.ui.components.LyroLoadingState
import com.lyro.app.ui.components.OnlineSongListItem
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
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isResolvingStream by viewModel.isResolvingStream.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val isOnlineMode by viewModel.isOnlineMode.collectAsState()
    val onlineResults by viewModel.onlineSearchResults.collectAsState()
    val isSearchingOnline by viewModel.isSearchingOnline.collectAsState()
    val onlineSearchError by viewModel.onlineSearchError.collectAsState()
    val downloadStatuses by viewModel.downloadStatuses.collectAsState()
    val lastCompletedDownload by viewModel.lastCompletedDownload.collectAsState()

    var selectedSongForMenu by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LyroBackground)
    ) {
        // Resolving Stream Banner
        if (isResolvingStream) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(LyroSurfaceElevated, RoundedCornerShape(10.dp))
                    .border(1.dp, LyroDivider, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = LyroAccent
                )
                Text(
                    text = "Resolving audio stream...",
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = LyroTextPrimary
                )
            }
        }

        // Playback Error Banner with Dismiss Button
        playbackError?.let { error ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(LyroError.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .border(1.dp, LyroError.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = error,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = LyroError,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { viewModel.clearPlaybackError() },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = LyroError,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Download Completed Banner
        lastCompletedDownload?.let { completed ->
            LaunchedEffect(completed.videoId) {
                kotlinx.coroutines.delay(3500)
                viewModel.clearLastCompletedDownload()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(LyroAccentMuted, RoundedCornerShape(10.dp))
                    .border(1.dp, LyroAccent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = LyroAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Downloaded: ${completed.title}",
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = LyroTextPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { viewModel.clearLastCompletedDownload() },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = LyroTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Modern Minimal Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(LyroSurfaceElevated, RoundedCornerShape(12.dp))
                    .border(1.dp, LyroDivider, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = LyroTextSecondary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = if (isOnlineMode) "Search YouTube Music songs, artists..." else "Search local tracks, artists...",
                            color = LyroTextMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        singleLine = true,
                        cursorBrush = SolidColor(LyroAccent),
                        textStyle = TextStyle(
                            color = LyroTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.onSearchQueryChanged("") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = LyroTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (isOnlineMode) {
            // ONLINE SEARCH CONTENT
            if (isSearchingOnline) {
                LyroLoadingState(
                    title = "Searching Online Music...",
                    subtitle = "Finding tracks for \"$searchQuery\""
                )
            } else if (onlineSearchError != null) {
                LyroEmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "No Tracks Found",
                    description = onlineSearchError ?: "No matching results found.",
                    primaryButtonText = "Clear Search",
                    onPrimaryButtonClick = { viewModel.onSearchQueryChanged("") }
                )
            } else if (onlineResults.isEmpty()) {
                LyroEmptyState(
                    icon = Icons.Default.MusicNote,
                    title = "Discover Online Music",
                    description = "Search millions of songs, artists, and albums for instant online streaming.",
                    tips = listOf(
                        "Search by song title, artist, or album",
                        "Tap any track to begin instant streaming",
                        "Download any song with 1-click for offline playback"
                    )
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp)
                ) {
                    items(
                        items = onlineResults,
                        key = { it.videoId },
                        contentType = { "online_item" }
                    ) { track ->
                        val status = downloadStatuses[track.videoId]
                            ?: if (viewModel.isTrackDownloaded(track)) DownloadStatus.Completed else DownloadStatus.Idle

                        OnlineSongListItem(
                            track = track,
                            isCurrentTrack = currentTrack?.id == track.videoId,
                            isPlaying = isPlaying && currentTrack?.id == track.videoId,
                            isResolving = isResolvingStream && currentTrack?.id == track.videoId,
                            downloadStatus = status,
                            onDownloadClick = { viewModel.downloadTrack(track) },
                            onClick = { viewModel.playOnlineTrack(track, onlineResults) }
                        )
                    }
                }
            }
        } else {
            // LOCAL MUSIC CONTENT
            if (!hasPermission) {
                LyroEmptyState(
                    icon = Icons.Default.Security,
                    title = "Storage Access Required",
                    description = "Lyro needs storage access to find and play audio files on your device.",
                    primaryButtonText = "Allow Storage Access",
                    onPrimaryButtonClick = onRequestPermission,
                    secondaryButtonText = "Stream Online Instead",
                    onSecondaryButtonClick = { viewModel.setOnlineMode(true) }
                )
            } else if (isLoading) {
                LyroLoadingState(
                    title = "Scanning Local Library...",
                    subtitle = "Searching and indexing audio tracks on your device..."
                )
            } else {
                // Filter & Sort Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LyroChip(
                        text = "All Tracks (${songs.size})",
                        selected = !favoritesOnly,
                        onClick = { viewModel.setFavoritesFilter(false) }
                    )

                    LyroChip(
                        text = "Favorites ♥",
                        selected = favoritesOnly,
                        onClick = { viewModel.setFavoritesFilter(true) }
                    )

                    LyroChip(
                        text = if (sortOrder == SortOrder.TITLE) "A-Z ✓" else "A-Z",
                        selected = sortOrder == SortOrder.TITLE,
                        onClick = { viewModel.onSortOrderChanged(SortOrder.TITLE) }
                    )

                    LyroChip(
                        text = if (sortOrder == SortOrder.DATE_ADDED) "Newest ✓" else "Newest",
                        selected = sortOrder == SortOrder.DATE_ADDED,
                        onClick = { viewModel.onSortOrderChanged(SortOrder.DATE_ADDED) }
                    )

                    LyroChip(
                        text = if (sortOrder == SortOrder.DURATION) "Length ✓" else "Length",
                        selected = sortOrder == SortOrder.DURATION,
                        onClick = { viewModel.onSortOrderChanged(SortOrder.DURATION) }
                    )
                }

                // Songs List or Empty State
                if (songs.isEmpty()) {
                    if (favoritesOnly) {
                        LyroEmptyState(
                            icon = Icons.Default.FavoriteBorder,
                            title = "No Favorite Songs",
                            description = "You haven't marked any songs as favorites yet. Tap the heart on any track to add it here.",
                            primaryButtonText = "View All Tracks",
                            onPrimaryButtonClick = { viewModel.setFavoritesFilter(false) },
                            secondaryButtonText = "Search Online",
                            onSecondaryButtonClick = { viewModel.setOnlineMode(true) }
                        )
                    } else if (searchQuery.isNotBlank()) {
                        LyroEmptyState(
                            icon = Icons.Default.SearchOff,
                            title = "No Matches Found",
                            description = "No local tracks matched \"$searchQuery\". Try searching online instead.",
                            primaryButtonText = "Search Online for \"${searchQuery.take(20)}\"",
                            onPrimaryButtonClick = { viewModel.setOnlineMode(true) },
                            secondaryButtonText = "Clear Filter",
                            onSecondaryButtonClick = { viewModel.onSearchQueryChanged("") }
                        )
                    } else {
                        LyroEmptyState(
                            icon = Icons.Default.MusicOff,
                            title = "No Songs Found",
                            description = "No audio files were detected on your device storage.",
                            tips = listOf(
                                "Add audio files (.mp3, .m4a, .flac) to your Music or Download folder",
                                "Tap 'Rescan Storage' to refresh your library",
                                "Or stream music online using the Online switch above"
                            ),
                            primaryButtonText = "Rescan Storage",
                            onPrimaryButtonClick = { viewModel.loadSongs() },
                            secondaryButtonText = "Switch to Online Search",
                            onSecondaryButtonClick = { viewModel.setOnlineMode(true) }
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp)
                    ) {
                        items(
                            items = songs,
                            key = { it.id },
                            contentType = { "song_row" }
                        ) { song ->
                            SongListItem(
                                song = song,
                                isCurrentSong = currentSong?.id == song.id,
                                isPlaying = isPlaying && currentSong?.id == song.id,
                                onClick = { viewModel.playSong(song, songs) },
                                onFavoriteToggle = { viewModel.toggleFavorite(song) },
                                onMoreClick = { selectedSongForMenu = song }
                            )
                        }
                    }
                }
            }
        }
    }

    // Playlist Add Modal Sheet
    selectedSongForMenu?.let { song ->
        ModalBottomSheet(
            onDismissRequest = { selectedSongForMenu = null },
            containerColor = LyroSurfaceElevated,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .background(LyroTextMuted.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Add to Mixtape",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LyroTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${song.title} • ${song.artist}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = LyroTextSecondary,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(16.dp))

                LyroButton(
                    onClick = { showCreatePlaylistDialog = true },
                    backgroundColor = LyroAccent,
                    contentColor = Color.Black,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Create New Mixtape",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (playlists.isEmpty()) {
                    Text(
                        text = "No mixtapes created yet.",
                        fontSize = 13.sp,
                        color = LyroTextMuted,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    playlists.forEach { pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(LyroSurface)
                                .clickable {
                                    viewModel.addSongToPlaylist(pl.id, song.id)
                                    selectedSongForMenu = null
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = pl.name,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = LyroTextPrimary
                            )
                            Text(
                                text = "${pl.songCount} tracks",
                                fontSize = 12.sp,
                                color = LyroTextSecondary
                            )
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
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName, (0..6).random())
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                        }
                    }
                ) {
                    Text("Create", color = LyroAccent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
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
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
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
