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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyro.app.core.designsystem.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import com.lyro.app.data.download.DownloadStatus
import com.lyro.app.ui.components.OnlineSongListItem
import com.lyro.app.ui.components.SongListItem
import com.lyro.app.ui.components.SongRow
import com.lyro.app.ui.home.SectionHeader
import com.lyro.app.ui.home.SquareArtworkCard
import com.lyro.app.ui.songs.SongsViewModel

@Composable
fun ExploreScreen(
    viewModel: SongsViewModel,
    listState: LazyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    contentPadding: PaddingValues = PaddingValues(bottom = 140.dp),
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val localSongs by viewModel.songs.collectAsState()
    val onlineResults by viewModel.onlineSearchResults.collectAsState()
    val isSearchingOnline by viewModel.isSearchingOnline.collectAsState()
    val onlineSearchError by viewModel.onlineSearchError.collectAsState()
    val exploreTrending by viewModel.exploreTrending.collectAsState()
    val isExploreLoading by viewModel.isExploreLoading.collectAsState()
    val downloadStatuses by viewModel.downloadStatuses.collectAsState()

    val currentSong by viewModel.currentSong.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isResolvingStream by viewModel.isResolvingStream.collectAsState()

    // Back button clears active search query first before delegating to parent
    BackHandler(enabled = searchQuery.isNotBlank()) {
        viewModel.onSearchQueryChanged("")
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(LyroBackground),
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

        // IF ACTIVE SEARCH: Show Unified Results (On this device + Online)
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

                items(localSongs.take(5), key = { "local_${it.id}" }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = currentSong?.id == song.id,
                        isPlaying = isPlaying && currentSong?.id == song.id,
                        onClick = { viewModel.playSong(song) },
                        onMoreClick = { viewModel.toggleFavorite(song) }
                    )
                }
            }

            // B. Online YouTube Results
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
                        onClick = { viewModel.playOnlineTrack(track) }
                    )
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
                                onClick = { viewModel.playOnlineTrack(track, exploreTrending) }
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
                            onClick = { viewModel.playOnlineTrack(track, exploreTrending) }
                        )
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
