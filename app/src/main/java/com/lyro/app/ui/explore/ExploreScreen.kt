package com.lyro.app.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import coil.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import com.lyro.app.data.download.DownloadStatus
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.ui.components.OnlineSongListItem
import com.lyro.app.ui.components.SongListItem
import com.lyro.app.ui.components.SongRow
import com.lyro.app.ui.home.SectionHeader
import com.lyro.app.ui.home.SquareArtworkCard
import com.lyro.app.ui.songs.SongsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: SongsViewModel,
    listState: LazyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    contentPadding: PaddingValues = PaddingValues(bottom = 140.dp),
    modifier: Modifier = Modifier
) {
    val haptics = com.lyro.app.core.haptics.rememberLyroHaptics()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val localSongs by viewModel.songs.collectAsState()
    val onlineResults by viewModel.onlineSearchResults.collectAsState()
    val isSearchingOnline by viewModel.isSearchingOnline.collectAsState()
    val onlineSearchError by viewModel.onlineSearchError.collectAsState()
    val exploreTrending by viewModel.exploreTrending.collectAsState()
    val isExploreLoading by viewModel.isExploreLoading.collectAsState()
    val isExploreRefreshing by viewModel.isExploreRefreshing.collectAsState()
    val exploreRefreshError by viewModel.exploreRefreshError.collectAsState()
    val downloadStatuses by viewModel.downloadStatuses.collectAsState()

    val currentSong by viewModel.currentSong.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val likedTracks by viewModel.likedTracks.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isResolvingStream by viewModel.isResolvingStream.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var selectedTrackForOptions by remember { mutableStateOf<OnlineTrack?>(null) }

    // Pull-to-refresh state: only enabled when Explore list is at the very top
    val pullRefreshState = rememberPullToRefreshState(
        enabled = { !listState.canScrollBackward || (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) }
    )

    // Haptics: Subtle selection tick when crossing pull threshold
    var hasCrossedThreshold by remember { mutableStateOf(false) }
    LaunchedEffect(pullRefreshState.progress) {
        if (pullRefreshState.progress >= 1.0f && !hasCrossedThreshold && !pullRefreshState.isRefreshing) {
            hasCrossedThreshold = true
            haptics.selection()
        } else if (pullRefreshState.progress < 1.0f) {
            hasCrossedThreshold = false
        }
    }

    // Trigger refresh when released past threshold
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing && !isExploreRefreshing) {
            if (isOnline) {
                if (searchQuery.isNotBlank()) {
                    viewModel.searchOnline(searchQuery)
                } else {
                    viewModel.refreshExplore()
                }
            } else {
                pullRefreshState.endRefresh()
            }
        }
    }

    // Haptics: Light click when refresh finishes
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isExploreRefreshing, isSearchingOnline) {
        val refreshing = isExploreRefreshing || (searchQuery.isNotBlank() && isSearchingOnline)
        if (refreshing) {
            wasRefreshing = true
            pullRefreshState.startRefresh()
        } else {
            pullRefreshState.endRefresh()
            if (wasRefreshing) {
                wasRefreshing = false
                haptics.click()
            }
        }
    }

    // Auto-clear transient refresh error
    LaunchedEffect(exploreRefreshError) {
        if (exploreRefreshError != null) {
            kotlinx.coroutines.delay(4000)
            viewModel.clearExploreRefreshError()
        }
    }

    // Back button clears active search query first before delegating to parent
    BackHandler(enabled = searchQuery.isNotBlank()) {
        viewModel.onSearchQueryChanged("")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LyroBackground)
            .clipToBounds()
            .nestedScroll(pullRefreshState.nestedScrollConnection)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding
        ) {
        // 1. Top Title & Unified Search Bar
        item(key = "explore_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Explore",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = LyroTextPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Modern Search Field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LyroSurfaceElevated)
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
                                text = "Search tracks, artists, albums...",
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
        }

        // IF ACTIVE SEARCH: Show Unified Results (On this device + Online if connected)
        if (searchQuery.isNotBlank()) {
            // A. Local Results on Device
            if (localSongs.isNotEmpty()) {
                item(key = "search_local_header") {
                    SectionHeader(
                        title = "On this device",
                        subtitle = "${localSongs.size} matching local tracks"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(if (isOnline) localSongs.take(5) else localSongs, key = { "local_${it.id}" }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = currentSong?.id == song.id,
                        isPlaying = isPlaying && currentSong?.id == song.id,
                        onClick = { viewModel.playSong(song, localSongs) },
                        onMoreClick = { viewModel.toggleFavorite(song) }
                    )
                }
            } else if (!isOnline) {
                item(key = "offline_no_local_matches") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching tracks found on this device",
                            color = LyroTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // B. Online YouTube Results (only when online)
            if (isOnline) {
                item(key = "search_online_header") {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionHeader(
                        title = "Online Results",
                        subtitle = "Stream or download with 1-click"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (isSearchingOnline) {
                    item(key = "search_online_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = LyroAccent,
                                strokeWidth = 2.5.dp
                            )
                        }
                    }
                } else if (onlineSearchError != null) {
                    item(key = "search_online_error") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = onlineSearchError ?: "No tracks found",
                                color = LyroTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    items(onlineResults, key = { "online_${it.videoId}" }) { track ->
                        val status = downloadStatuses[track.videoId]
                            ?: if (viewModel.isTrackDownloaded(track)) DownloadStatus.Completed else DownloadStatus.Idle

                        OnlineSongListItem(
                            track = track,
                            isCurrentTrack = currentTrack?.id == track.videoId,
                            isPlaying = isPlaying && currentTrack?.id == track.videoId,
                            isResolving = isResolvingStream && currentTrack?.id == track.videoId,
                            downloadStatus = status,
                            onDownloadClick = { viewModel.downloadTrack(track) },
                            onMoreClick = { selectedTrackForOptions = track },
                            onClick = { viewModel.playOnlineTrack(track, onlineResults) }
                        )
                    }
                }
            }
        } else if (!isOnline) {
            // Clean minimal offline state when not searching
            item(key = "explore_offline_state") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(LyroSurfaceElevated, CircleShape)
                                .border(1.dp, LyroDivider, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = LyroTextMuted,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "You're offline",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LyroTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Downloaded music is still available in Home and Library.",
                            fontSize = 13.sp,
                            color = LyroTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // DEFAULT EXPLORE VIEW: Discovery Rails

            // 2. Explore Category Cards Rail
            item(key = "explore_categories") {
                val categories = listOf(
                    Triple("New Releases", Icons.Default.FiberNew, "New Music Hits"),
                    Triple("Charts", Icons.AutoMirrored.Filled.TrendingUp, "Top Charts 2024"),
                    Triple("Moods & Genres", Icons.Default.Mood, "Chill Beats"),
                    Triple("Workout", Icons.Default.FitnessCenter, "Workout Motivation Music")
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(categories) { (name, icon, query) ->
                        ExploreCategoryCard(
                            title = name,
                            icon = icon,
                            onClick = { viewModel.onSearchQueryChanged(query) }
                        )
                    }
                }
            }

            // 3. Trending Now Section
            item(key = "explore_trending_header") {
                Spacer(modifier = Modifier.height(18.dp))
                SectionHeader(
                    title = "Trending Now",
                    subtitle = "Global top music"
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (isExploreLoading && exploreTrending.isEmpty()) {
                item(key = "trending_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = LyroAccent,
                            strokeWidth = 2.5.dp
                        )
                    }
                }
            } else {
                item(key = "trending_rail") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(exploreTrending.take(10), key = { it.videoId }) { track ->
                            SquareArtworkCard(
                                title = track.title,
                                subtitle = track.artist,
                                thumbnailUrl = track.thumbnailUrl,
                                onClick = { viewModel.playOnlineTrack(track, exploreTrending) },
                                onLongClick = { selectedTrackForOptions = track }
                            )
                        }
                    }
                }

                // 4. Popular Online Hits List
                if (exploreTrending.size > 10) {
                    item(key = "popular_hits_header") {
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionHeader(
                            title = "Popular Tracks",
                            subtitle = "Stream instantly"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(exploreTrending.drop(10), key = { it.videoId }) { track ->
                        val status = downloadStatuses[track.videoId]
                            ?: if (viewModel.isTrackDownloaded(track)) DownloadStatus.Completed else DownloadStatus.Idle

                        OnlineSongListItem(
                            track = track,
                            isCurrentTrack = currentTrack?.id == track.videoId,
                            isPlaying = isPlaying && currentTrack?.id == track.videoId,
                            isResolving = isResolvingStream && currentTrack?.id == track.videoId,
                            downloadStatus = status,
                            onDownloadClick = { viewModel.downloadTrack(track) },
                            onMoreClick = { selectedTrackForOptions = track },
                            onClick = { viewModel.playOnlineTrack(track, exploreTrending) }
                        )
                    }
                }
            }
        }
    }

    // Material3 Pull-to-Refresh Indicator
    PullToRefreshContainer(
        state = pullRefreshState,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .graphicsLayer {
                alpha = if (pullRefreshState.verticalOffset > 0f || pullRefreshState.isRefreshing) 1f else 0f
            },
        containerColor = LyroSurfaceElevated,
        contentColor = LyroAccent
    )

    // Subtle transient notification on failure / offline
    AnimatedVisibility(
        visible = exploreRefreshError != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 16.dp, start = 20.dp, end = 20.dp)
    ) {
        Surface(
            color = LyroSurfaceElevated,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, LyroSurfaceHighlight)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = LyroAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = exploreRefreshError ?: "",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = LyroTextPrimary
                )
            }
        }
    }

    // ModalBottomSheet for track options (including Start Radio and Not Interested)
    selectedTrackForOptions?.let { track ->
        ModalBottomSheet(
            onDismissRequest = { selectedTrackForOptions = null },
            containerColor = LyroSurfaceElevated,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(LyroDivider)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                // Track header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(LyroSurface)
                    ) {
                        if (!track.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = track.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = LyroTextSecondary,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = LyroTextPrimary
                        )
                        Text(
                            text = track.artist,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = LyroTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = LyroDivider, thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Play
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedTrackForOptions = null
                            viewModel.playOnlineTrack(track)
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = LyroTextPrimary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Play", color = LyroTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }

                // Start Radio
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.click()
                            selectedTrackForOptions = null
                            viewModel.startSongRadio(track)
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
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
                            selectedTrackForOptions = null
                            viewModel.playNext(track)
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
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
                            selectedTrackForOptions = null
                            viewModel.addToQueue(track)
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = LyroTextPrimary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Add to Queue", color = LyroTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }

                // Download
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.click()
                            selectedTrackForOptions = null
                            viewModel.downloadTrack(track)
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = LyroTextPrimary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Download", color = LyroTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }

                // Favorite Toggle (Unified PlayableTrack)
                val isFav = likedTracks.any { 
                    (it.onlineVideoId != null && it.onlineVideoId == track.onlineVideoId) ||
                    it.id == track.id
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.click()
                            viewModel.toggleFavorite(track)
                            selectedTrackForOptions = null
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFav) LyroAccent else LyroTextPrimary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (isFav) "Remove from Favorites" else "Save to Favorites",
                        color = LyroTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Not interested
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.click()
                            viewModel.markNotInterested(track)
                            selectedTrackForOptions = null
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Block, contentDescription = null, tint = Color(0xFFEF5350))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Not interested", color = Color(0xFFEF5350), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("Don't recommend this track again", color = LyroTextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
}

@Composable
fun ExploreCategoryCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(LyroSurfaceElevated)
            .border(1.dp, LyroDivider, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LyroAccent,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = LyroTextPrimary
        )
    }
}
