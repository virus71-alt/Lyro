package com.lyro.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.model.LocalTrack
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.data.model.Playlist
import com.lyro.app.data.model.Song
import com.lyro.app.data.model.UnifiedTrack
import com.lyro.app.data.model.toLocalTrack
import com.lyro.app.ui.components.SongArtworkThumbnail
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import com.lyro.app.R
import com.lyro.app.ui.components.SongRow
import com.lyro.app.ui.songs.SongsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: SongsViewModel,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLikedSongsClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    listState: LazyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    contentPadding: PaddingValues = PaddingValues(bottom = 140.dp),
    modifier: Modifier = Modifier
) {
    val haptics = com.lyro.app.core.haptics.rememberLyroHaptics()
    val homeQuickPicks by viewModel.homeQuickPicks.collectAsState()
    val homeTrending by viewModel.homeTrending.collectAsState()
    val homeRecommended by viewModel.homeRecommended.collectAsState()
    val isHomeLoading by viewModel.isHomeLoading.collectAsState()
    val isHomeRefreshing by viewModel.isHomeRefreshing.collectAsState()
    val homeRefreshError by viewModel.homeRefreshError.collectAsState()
    val homeDiscover by viewModel.homeDiscover.collectAsState()
    val likedTracks by viewModel.likedTracks.collectAsState()
    val recentlyAdded by viewModel.recentlyAdded.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val selectedMood by viewModel.selectedMood.collectAsState()
    val moodTracks by viewModel.moodTracks.collectAsState()
    val isMoodLoading by viewModel.isMoodLoading.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val isOfflineEmpty by viewModel.isOfflineEmpty.collectAsState()
    val localSongs by viewModel.songs.collectAsState()
    val downloadedTracks = remember(localSongs, isOnline) {
        localSongs.filter { viewModel.isTrackDownloaded(it.toLocalTrack()) }
    }

    val currentSong by viewModel.currentSong.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    var selectedTrackForOptions by remember { mutableStateOf<PlayableTrack?>(null) }

    // Pull-to-refresh state: only enabled when Home list is scrolled to the very top
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

    // Trigger refresh when user releases past threshold
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing && !isHomeRefreshing) {
            viewModel.refreshHome()
        }
    }

    // Haptics: Light click when refresh completes
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isHomeRefreshing) {
        if (isHomeRefreshing) {
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
    LaunchedEffect(homeRefreshError) {
        if (homeRefreshError != null) {
            kotlinx.coroutines.delay(4000)
            viewModel.clearHomeRefreshError()
        }
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
        // 1. Top App Bar: Clean Branding + Search & Settings Actions
        item(key = "home_top_bar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "Lyro Logo",
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(7.dp))
                    )
                    Text(
                        text = "LYRO",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = LyroTextPrimary,
                        letterSpacing = 1.5.sp
                    )
                    if (!isOnline) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(LyroSurfaceElevated)
                                .border(1.dp, LyroDivider, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline",
                                tint = LyroTextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Offline",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = LyroTextSecondary
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = {
                        haptics.click()
                        onSearchClick()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = LyroTextPrimary
                        )
                    }
                    IconButton(onClick = {
                        haptics.click()
                        onSettingsClick()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = LyroTextPrimary
                        )
                    }
                }
            }
        }

        // Empty offline state if device has zero music
        if (isOfflineEmpty) {
            item(key = "empty_offline_state") {
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
                            text = "No downloaded music available.\nConnect to the internet to discover music.",
                            fontSize = 13.sp,
                            color = LyroTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // 2. Horizontally scrollable mood / activity chips (Online only)
        if (isOnline) {
            item(key = "home_mood_chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.moodChips, key = { it }) { mood ->
                        val isSelected = selectedMood == mood
                        val chipBg = if (isSelected) LyroSurfaceHighlight else LyroSurfaceElevated
                        val textColor = if (isSelected) LyroAccent else LyroTextSecondary
                        val chipBorder = if (isSelected) LyroAccent.copy(alpha = 0.5f) else Color.Transparent

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(chipBg)
                                .border(1.dp, chipBorder, RoundedCornerShape(20.dp))
                                .clickable {
                                    haptics.selection()
                                    viewModel.selectMood(mood)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mood,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }

        // 3. Mood Discovery Section (if mood is selected and online)
        if (isOnline && selectedMood != null) {
            item(key = "mood_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                ) {
                    SectionHeader(
                        title = "$selectedMood Mix",
                        subtitle = "Online curated energy",
                        actionText = null
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isMoodLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = LyroAccent,
                                strokeWidth = 2.dp
                            )
                        }
                    } else if (moodTracks.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(moodTracks, key = { it.videoId }) { track ->
                                SquareArtworkCard(
                                    title = track.title,
                                    subtitle = track.artist,
                                    thumbnailUrl = track.thumbnailUrl,
                                    onClick = { viewModel.playOnlineTrack(track, moodTracks) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. QUICK PICKS (Multi-row horizontally scrollable columns combining online & local)
        if (homeQuickPicks.isNotEmpty()) {
            item(key = "quick_picks_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                ) {
                    SectionHeader(
                        title = "Quick picks",
                        subtitle = "Start radio from a song",
                        actionText = "Play all",
                        onActionClick = { viewModel.playQuickPicks() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Chunk quick picks into columns of 4 stacked tracks
                    val columns = remember(homeQuickPicks) { homeQuickPicks.chunked(4) }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(columns, key = { col -> col.firstOrNull()?.let { it.onlineVideoId ?: it.id } ?: col.hashCode().toString() }) { columnTracks ->
                            Column(
                                modifier = Modifier.width(310.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                columnTracks.forEach { track ->
                                    val isCur = currentTrack?.id == track.id || currentSong?.title.equals(track.title, ignoreCase = true)
                                    SongRow(
                                        track = track,
                                        isCurrent = isCur,
                                        isPlaying = isPlaying && isCur,
                                        onClick = { viewModel.playTrack(track, homeQuickPicks) },
                                        onMoreClick = { selectedTrackForOptions = track }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (isHomeLoading) {
            item(key = "home_loading_placeholder") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = LyroAccent,
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        // 5. TRENDING NOW RAIL (Online hits worldwide - Online only)
        if (isOnline && homeTrending.isNotEmpty()) {
            item(key = "home_trending_rail") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                ) {
                    SectionHeader(
                        title = "Trending Now",
                        subtitle = "Popular worldwide",
                        actionText = null
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(homeTrending, key = { "trend_${it.id}" }) { track ->
                            SquareArtworkCard(
                                title = track.title,
                                subtitle = track.artist,
                                track = track,
                                isDownloaded = track.isDownloaded,
                                onClick = { viewModel.playTrack(track, homeTrending) },
                                onLongClick = { selectedTrackForOptions = track }
                            )
                        }
                    }
                }
            }
        }

        // 6. RECOMMENDED RAIL (Personalized for you - Online only)
        if (isOnline && homeRecommended.isNotEmpty()) {
            item(key = "home_recommended_rail") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                ) {
                    SectionHeader(
                        title = "Recommended for you",
                        subtitle = "Personalized for your taste",
                        actionText = null
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(homeRecommended, key = { "rec_${it.id}" }) { track ->
                            SquareArtworkCard(
                                title = track.title,
                                subtitle = track.artist,
                                track = track,
                                isDownloaded = track.isDownloaded,
                                onClick = { viewModel.playTrack(track, homeRecommended) },
                                onLongClick = { selectedTrackForOptions = track }
                            )
                        }
                    }
                }
            }
        }

        // 7. DISCOVER SOMETHING NEW RAIL (Adjacent discovery & fresh exploratory tracks - Online only)
        if (isOnline && homeDiscover.isNotEmpty()) {
            item(key = "home_discover_rail") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                ) {
                    SectionHeader(
                        title = "Discover something new",
                        subtitle = "Adjacent artists & fresh sounds",
                        actionText = null
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(homeDiscover, key = { "disc_${it.id}" }) { track ->
                            SquareArtworkCard(
                                title = track.title,
                                subtitle = track.artist,
                                track = track,
                                isDownloaded = track.isDownloaded,
                                onClick = { viewModel.playTrack(track, homeDiscover) },
                                onLongClick = { selectedTrackForOptions = track }
                            )
                        }
                    }
                }
            }
        }

        // 8. DOWNLOADED RAIL (Featured offline tracks)
        if (!isOnline && downloadedTracks.isNotEmpty()) {
            item(key = "home_downloaded_rail") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                ) {
                    SectionHeader(
                        title = "Downloaded",
                        subtitle = "${downloadedTracks.size} tracks available offline",
                        actionText = "Play all",
                        onActionClick = {
                            if (downloadedTracks.isNotEmpty()) {
                                viewModel.playSong(downloadedTracks.first(), downloadedTracks)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(downloadedTracks, key = { "dl_${it.id}" }) { song ->
                            SquareArtworkCard(
                                title = song.title,
                                subtitle = song.artist,
                                track = song.toLocalTrack(),
                                isDownloaded = true,
                                onClick = { viewModel.playSong(song, downloadedTracks) },
                                onLongClick = { viewModel.toggleFavorite(song) }
                            )
                        }
                    }
                }
            }
        }

        // 5. LIKED SONGS RAIL (Unified Local & Online)
        if (likedTracks.isNotEmpty()) {
            item(key = "liked_tracks_rail") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                ) {
                    SectionHeader(
                        title = "Liked songs",
                        subtitle = "${likedTracks.size} tracks saved",
                        actionText = "See all",
                        onActionClick = onLikedSongsClick
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(likedTracks, key = { it.onlineVideoId ?: it.id }) { track ->
                            SquareArtworkCard(
                                title = track.title,
                                subtitle = track.artist,
                                track = track,
                                isDownloaded = track.isDownloaded,
                                onClick = { viewModel.playTrack(track, likedTracks) },
                                onLongClick = { selectedTrackForOptions = track }
                            )
                        }
                    }
                }
            }
        }

        // 6. RECENTLY ADDED RAIL
        if (recentlyAdded.isNotEmpty()) {
            item(key = "recently_added_rail") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                ) {
                    SectionHeader(
                        title = "Recently Added",
                        subtitle = "Fresh in your collection",
                        actionText = null
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(recentlyAdded, key = { it.id }) { song ->
                            SquareArtworkCard(
                                title = song.title,
                                subtitle = song.artist,
                                song = song,
                                onClick = { viewModel.playSong(song, recentlyAdded) }
                            )
                        }
                    }
                }
            }
        }

        // 7. YOUR MIXTAPES RAIL
        if (playlists.isNotEmpty()) {
            item(key = "playlists_rail") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                ) {
                    SectionHeader(
                        title = "Your Mixtapes",
                        subtitle = "Custom collections",
                        actionText = null
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            PlaylistRailCard(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) }
                            )
                        }
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
        visible = homeRefreshError != null,
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
                    text = homeRefreshError ?: "",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = LyroTextPrimary
                )
            }
        }
    }

    // ModalBottomSheet for track options (including Not Interested)
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
                        .clip(CircleShape)
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
                        if (!track.artworkUriString.isNullOrBlank()) {
                            AsyncImage(
                                model = track.artworkUriString,
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
                            viewModel.playTrack(track)
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

                // Not Interested (Penalizes track in recommendation brain)
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
                        Text("Tune recommendations away from this track", color = LyroTextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(
                    text = subtitle.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    color = LyroTextSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = LyroTextPrimary
            )
        }

        if (actionText != null && onActionClick != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(LyroSurfaceElevated)
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LyroTextPrimary
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SquareArtworkCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    song: Song? = null,
    track: com.lyro.app.data.model.PlayableTrack? = null,
    thumbnailUrl: String? = null,
    isDownloaded: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val haptics = com.lyro.app.core.haptics.rememberLyroHaptics()
    val cardWidth = 140.dp
    val artShape = RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .width(cardWidth)
            .combinedClickable(
                onClick = {
                    haptics.click()
                    onClick()
                },
                onLongClick = if (onLongClick != null) {
                    {
                        haptics.longPress()
                        onLongClick.invoke()
                    }
                } else null
            )
    ) {
        Box(
            modifier = Modifier
                .size(cardWidth)
                .clip(artShape)
                .background(LyroSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            val art = track?.artworkUriString ?: thumbnailUrl
            if (song != null) {
                SongArtworkThumbnail(song = song, size = cardWidth)
            } else if (!art.isNullOrBlank()) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val density = androidx.compose.ui.platform.LocalDensity.current
                val targetPx = remember(cardWidth, density) { with(density) { cardWidth.roundToPx() } }
                val imageRequest = remember(art, targetPx) {
                    coil.request.ImageRequest.Builder(context)
                        .data(art)
                        .size(targetPx, targetPx)
                        .crossfade(true)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = LyroTextMuted,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = LyroTextPrimary
        )

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isDownloaded || track?.isDownloaded == true || song != null) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Downloaded",
                    tint = LyroAccent.copy(alpha = 0.85f),
                    modifier = Modifier.size(11.dp)
                )
            }
            Text(
                text = subtitle,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = LyroTextSecondary
            )
        }
    }
}

@Composable
fun PlaylistRailCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardWidth = 140.dp
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .width(cardWidth)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(cardWidth)
                .clip(shape)
                .background(LyroSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = LyroAccent,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playlist.name,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
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
