package com.lyro.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.local.DownloadedMetadata
import com.lyro.app.data.model.Playlist
import com.lyro.app.data.model.Song
import com.lyro.app.data.model.toLocalTrack
import com.lyro.app.data.repository.SortOrder
import com.lyro.app.ui.components.LyroEmptyState
import com.lyro.app.ui.components.SongRow
import com.lyro.app.ui.home.SectionHeader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import com.lyro.app.ui.songs.SongsViewModel

enum class LibraryFilter {
    ALL,
    PLAYLISTS,
    LIKED,
    DOWNLOADS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: SongsViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    listState: LazyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    contentPadding: PaddingValues = PaddingValues(bottom = 140.dp),
    modifier: Modifier = Modifier
) {
    val haptics = com.lyro.app.core.haptics.rememberLyroHaptics()
    val songs by viewModel.songs.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val likedTracks by viewModel.likedTracks.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    val downloadedList by viewModel.downloadedMetadataList.collectAsState()
    var downloadSubFilter by rememberSaveable { mutableStateOf("ALL") }

    var selectedFilter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var selectedSongForMenu by remember { mutableStateOf<Song?>(null) }
    var viewingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var viewingPlaylistSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    LaunchedEffect(viewingPlaylist) {
        val pl = viewingPlaylist
        if (pl != null) {
            viewingPlaylistSongs = viewModel.getSongsForPlaylist(pl.id)
        } else {
            viewingPlaylistSongs = emptyList()
        }
    }

    // If a filter is active, back press resets filter to ALL before leaving Library
    BackHandler(enabled = selectedFilter != LibraryFilter.ALL) {
        haptics.selection()
        selectedFilter = LibraryFilter.ALL
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(LyroBackground),
        contentPadding = contentPadding
    ) {
        // 1. Top Header
        item(key = "library_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Library",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = LyroTextPrimary
                )

                IconButton(
                    onClick = {
                        haptics.click()
                        showCreatePlaylistDialog = true
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Mixtape",
                        tint = LyroAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 2. Category Filter Chips
        item(key = "library_filter_chips") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LyroChip(
                    text = "All Tracks (${songs.size})",
                    selected = selectedFilter == LibraryFilter.ALL,
                    onClick = {
                        if (selectedFilter != LibraryFilter.ALL) {
                            haptics.selection()
                            selectedFilter = LibraryFilter.ALL
                        }
                    }
                )

                LyroChip(
                    text = "Playlists (${playlists.size})",
                    selected = selectedFilter == LibraryFilter.PLAYLISTS,
                    onClick = {
                        if (selectedFilter != LibraryFilter.PLAYLISTS) {
                            haptics.selection()
                            selectedFilter = LibraryFilter.PLAYLISTS
                        }
                    }
                )

                LyroChip(
                    text = "Liked Songs (${likedTracks.size})",
                    selected = selectedFilter == LibraryFilter.LIKED,
                    onClick = {
                        if (selectedFilter != LibraryFilter.LIKED) {
                            haptics.selection()
                            selectedFilter = LibraryFilter.LIKED
                        }
                    }
                )

                LyroChip(
                    text = "Downloads (${downloadedList.size})",
                    selected = selectedFilter == LibraryFilter.DOWNLOADS,
                    onClick = {
                        if (selectedFilter != LibraryFilter.DOWNLOADS) {
                            haptics.selection()
                            selectedFilter = LibraryFilter.DOWNLOADS
                        }
                    }
                )

                if (selectedFilter == LibraryFilter.ALL) {
                    LyroChip(
                        text = if (sortOrder == SortOrder.TITLE) "Sort: A-Z" else "Sort: Title",
                        selected = sortOrder == SortOrder.TITLE,
                        onClick = {
                            if (sortOrder != SortOrder.TITLE) {
                                haptics.selection()
                                viewModel.onSortOrderChanged(SortOrder.TITLE)
                            }
                        }
                    )

                    LyroChip(
                        text = if (sortOrder == SortOrder.DATE_ADDED) "Sort: Newest" else "Sort: Date",
                        selected = sortOrder == SortOrder.DATE_ADDED,
                        onClick = {
                            if (sortOrder != SortOrder.DATE_ADDED) {
                                haptics.selection()
                                viewModel.onSortOrderChanged(SortOrder.DATE_ADDED)
                            }
                        }
                    )
                }
            }
        }

        // 3. Featured Liked Songs Banner (Shown when not filtering playlists or downloads)
        if (selectedFilter != LibraryFilter.PLAYLISTS && selectedFilter != LibraryFilter.DOWNLOADS) {
            item(key = "library_liked_card") {
                Spacer(modifier = Modifier.height(10.dp))
                val cardShape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(cardShape)
                        .background(LyroSurfaceElevated)
                        .border(1.dp, LyroDivider, cardShape)
                        .clickable { selectedFilter = LibraryFilter.LIKED }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(LyroAccentMuted, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Liked",
                                tint = LyroAccent,
                                modifier = Modifier.size(24.dp)
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
                                text = if (isOnline) "${likedTracks.size} tracks • Local & Online" else "${likedTracks.size} tracks • Available offline",
                                fontSize = 13.sp,
                                color = LyroTextSecondary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(LyroAccent, CircleShape)
                            .clickable {
                                if (likedTracks.isNotEmpty()) {
                                    viewModel.playTrack(likedTracks.first(), likedTracks)
                                } else if (likedSongs.isNotEmpty()) {
                                    viewModel.playSong(likedSongs.first(), likedSongs)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Liked",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 4. Playlists Section
        if (selectedFilter == LibraryFilter.ALL || selectedFilter == LibraryFilter.PLAYLISTS) {
            item(key = "playlists_section_header") {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(
                    title = "Mixtapes",
                    subtitle = "${playlists.size} playlists created",
                    actionText = "+ New",
                    onActionClick = { showCreatePlaylistDialog = true }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (playlists.isEmpty()) {
                item(key = "no_playlists") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "No mixtapes created yet. Tap + to make one.",
                            fontSize = 13.sp,
                            color = LyroTextMuted
                        )
                    }
                }
            } else {
                items(playlists, key = { "pl_${it.id}" }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onPlaylistClick(playlist)
                                viewingPlaylist = playlist
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(LyroSurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LibraryMusic,
                                contentDescription = null,
                                tint = LyroAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = LyroTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${playlist.songCount} tracks",
                                fontSize = 12.sp,
                                color = LyroTextSecondary
                            )
                        }
                    }
                }
            }
        }

        // 5. Tracks List Section
        if (selectedFilter == LibraryFilter.LIKED) {
            item(key = "tracks_section_header") {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(
                    title = "Liked Tracks",
                    subtitle = if (isOnline) "${likedTracks.size} tracks • Local & Online" else "${likedTracks.size} tracks • Available offline"
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (likedTracks.isEmpty()) {
                item(key = "empty_liked_tracks") {
                    LyroEmptyState(
                        icon = Icons.Default.FavoriteBorder,
                        title = "No Liked Tracks",
                        description = if (isOnline) {
                            "Tap the heart on any local or online song to save it to your liked tracks."
                        } else {
                            "No liked tracks are available offline. Connect to the internet or download tracks to view them here."
                        },
                        primaryButtonText = if (isOnline) "Explore Online Music" else "View All Tracks",
                        onPrimaryButtonClick = { selectedFilter = LibraryFilter.ALL }
                    )
                }
            } else {
                items(likedTracks, key = { "liked_${it.onlineVideoId ?: it.id}" }) { track ->
                    val isCur = currentTrack?.id == track.id || currentSong?.title.equals(track.title, ignoreCase = true)
                    SongRow(
                        track = track,
                        isCurrent = isCur,
                        isPlaying = isPlaying && isCur,
                        onClick = { viewModel.playTrack(track, likedTracks) },
                        onMoreClick = {
                            val localSong = (track as? com.lyro.app.data.model.LocalTrack)?.song
                                ?: (track as? com.lyro.app.data.model.UnifiedTrack)?.localSong
                            if (localSong != null) {
                                selectedSongForMenu = localSong
                            } else {
                                viewModel.toggleFavorite(track)
                            }
                        }
                    )
                }
            }
        } else if (selectedFilter == LibraryFilter.DOWNLOADS) {
            val smartCount = downloadedList.count { it.downloadOrigin == com.lyro.app.data.download.DownloadOrigin.SMART }
            val manualCount = downloadedList.count { it.downloadOrigin == com.lyro.app.data.download.DownloadOrigin.MANUAL }
            val filteredDownloads = when (downloadSubFilter) {
                "SMART" -> downloadedList.filter { it.downloadOrigin == com.lyro.app.data.download.DownloadOrigin.SMART }
                "MANUAL" -> downloadedList.filter { it.downloadOrigin == com.lyro.app.data.download.DownloadOrigin.MANUAL }
                else -> downloadedList
            }

            item(key = "downloads_section_header") {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(
                    title = "Downloads",
                    subtitle = "${downloadedList.size} offline tracks ($smartCount smart, $manualCount manual)"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LyroChip(
                        text = "All (${downloadedList.size})",
                        selected = downloadSubFilter == "ALL",
                        onClick = { downloadSubFilter = "ALL" }
                    )
                    LyroChip(
                        text = "Smart Mix ($smartCount)",
                        selected = downloadSubFilter == "SMART",
                        onClick = { downloadSubFilter = "SMART" }
                    )
                    LyroChip(
                        text = "Manual ($manualCount)",
                        selected = downloadSubFilter == "MANUAL",
                        onClick = { downloadSubFilter = "MANUAL" }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (filteredDownloads.isEmpty()) {
                item(key = "empty_downloads") {
                    LyroEmptyState(
                        icon = Icons.Default.Download,
                        title = "No Downloads Found",
                        description = if (downloadSubFilter == "SMART") {
                            "Smart Downloads hasn't saved any tracks yet. You can enable it in Settings."
                        } else if (downloadSubFilter == "MANUAL") {
                            "You haven't manually downloaded any tracks yet."
                        } else {
                            "No downloaded music available offline."
                        },
                        primaryButtonText = "View All Tracks",
                        onPrimaryButtonClick = { selectedFilter = LibraryFilter.ALL }
                    )
                }
            } else {
                items(filteredDownloads, key = { "dl_${it.videoId}" }) { item ->
                    val isSmart = item.downloadOrigin == com.lyro.app.data.download.DownloadOrigin.SMART
                    val onlineTrack = com.lyro.app.data.model.OnlineTrack(
                        videoId = item.videoId,
                        title = item.title,
                        artist = item.artist,
                        album = item.album,
                        thumbnailUrl = item.thumbnailUri,
                        durationMs = item.durationMs,
                        localUri = if (!item.localPath.isNullOrEmpty()) android.net.Uri.parse(item.localPath) else null
                    )
                    val isCur = currentTrack?.id == onlineTrack.id || currentTrack?.onlineVideoId == item.videoId
                    val sizeMb = item.fileSizeBytes / (1024.0 * 1024.0)
                    val sizeStr = if (sizeMb > 0) String.format(java.util.Locale.US, "%.1f MB", sizeMb) else ""

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                haptics.click()
                                viewModel.playTrack(onlineTrack)
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(LyroSurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSmart) Icons.Default.Check else Icons.Default.Download,
                                contentDescription = null,
                                tint = if (isSmart) LyroAccent else LyroTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isCur) LyroAccent else LyroTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.artist,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = LyroTextSecondary,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (sizeStr.isNotEmpty()) {
                                    Text(
                                        text = "• $sizeStr",
                                        fontSize = 11.sp,
                                        color = LyroTextMuted
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSmart) LyroAccent.copy(alpha = 0.2f) else LyroSurfaceHighlight)
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isSmart) "Smart Mix" else "Manual",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSmart) LyroAccent else LyroTextSecondary
                                    )
                                }
                            }
                        }

                        if (isSmart) {
                            IconButton(
                                onClick = {
                                    haptics.click()
                                    viewModel.promoteSmartDownloadToManual(item.videoId)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Keep offline",
                                    tint = LyroAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                haptics.click()
                                viewModel.deleteDownload(item.videoId)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove download",
                                tint = LyroTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        } else if (selectedFilter == LibraryFilter.ALL) {
            item(key = "tracks_section_header") {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(
                    title = "Device Tracks",
                    subtitle = "${songs.size} songs"
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (!hasPermission) {
                item(key = "permission_request") {
                    LyroEmptyState(
                        icon = Icons.Default.Security,
                        title = "Storage Access Required",
                        description = "Lyro needs storage access to scan audio files on this device.",
                        primaryButtonText = "Allow Storage Access",
                        onPrimaryButtonClick = onRequestPermission
                    )
                }
            } else if (songs.isEmpty()) {
                item(key = "empty_library") {
                    LyroEmptyState(
                        icon = Icons.Default.MusicOff,
                        title = "No Tracks Found",
                        description = "No local music found on device storage. Try rescan in settings or explore online music.",
                        primaryButtonText = "Rescan Storage",
                        onPrimaryButtonClick = { viewModel.loadSongs() }
                    )
                }
            } else {
                items(songs, key = { "song_${it.id}" }) { song ->
                    val isCur = currentSong?.id == song.id
                    SongRow(
                        song = song,
                        isCurrent = isCur,
                        isPlaying = isPlaying && isCur,
                        onClick = { viewModel.playSong(song, songs) },
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
                    text = song.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = LyroTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.artist} • ${song.formattedDuration()}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = LyroTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Start Radio
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.click()
                            selectedSongForMenu = null
                            viewModel.startSongRadio(song.toLocalTrack())
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Radio, contentDescription = null, tint = LyroAccent)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Start Radio", color = LyroAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Endless personalized queue from this song", color = LyroTextSecondary, fontSize = 12.sp)
                    }
                }

                // Play Next
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.click()
                            selectedSongForMenu = null
                            viewModel.playNext(song.toLocalTrack())
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.SkipNext, contentDescription = null, tint = LyroTextPrimary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Play Next", color = LyroTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }

                // Add to Queue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.click()
                            selectedSongForMenu = null
                            viewModel.addToQueue(song.toLocalTrack())
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = LyroTextPrimary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Add to Queue", color = LyroTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = LyroDivider, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Add to Mixtape",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LyroTextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

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

                Spacer(modifier = Modifier.height(14.dp))

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

    // Playlist Track Detail Modal Sheet
    viewingPlaylist?.let { playlist ->
        ModalBottomSheet(
            onDismissRequest = { viewingPlaylist = null },
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
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = LyroTextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${viewingPlaylistSongs.size} tracks",
                            fontSize = 13.sp,
                            color = LyroTextSecondary
                        )
                    }

                    if (viewingPlaylistSongs.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(LyroAccent, CircleShape)
                                .clickable {
                                    viewModel.playPlaylist(playlist)
                                    viewingPlaylist = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play All",
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (viewingPlaylistSongs.isEmpty()) {
                    Text(
                        text = "No tracks in this mixtape yet. Use the ⋮ menu on any song to add tracks here.",
                        fontSize = 13.sp,
                        color = LyroTextMuted,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(viewingPlaylistSongs, key = { "pl_song_${it.id}" }) { song ->
                            val isCur = currentSong?.id == song.id
                            SongRow(
                                song = song,
                                isCurrent = isCur,
                                isPlaying = isPlaying && isCur,
                                onClick = {
                                    viewModel.playSong(song, viewingPlaylistSongs)
                                    viewingPlaylist = null
                                },
                                onMoreClick = { selectedSongForMenu = song }
                            )
                        }
                    }
                }
            }
        }
    }
}
