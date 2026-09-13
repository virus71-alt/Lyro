package com.lyro.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lyro.app.core.designsystem.*
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.Playlist
import com.lyro.app.data.model.Song
import com.lyro.app.ui.components.SongArtworkThumbnail
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import com.lyro.app.ui.components.SongRow
import com.lyro.app.ui.songs.SongsViewModel

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
    val quickPicks by viewModel.quickPicks.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val recentlyAdded by viewModel.recentlyAdded.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val selectedMood by viewModel.selectedMood.collectAsState()
    val moodTracks by viewModel.moodTracks.collectAsState()
    val isMoodLoading by viewModel.isMoodLoading.collectAsState()

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(LyroBackground),
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
                Text(
                    text = "LYRO",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = LyroTextPrimary,
                    letterSpacing = 1.5.sp
                )
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

        // 2. Horizontally scrollable mood / activity chips
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

        // 3. Mood Discovery Section (if mood is selected)
        if (selectedMood != null) {
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

        // 4. QUICK PICKS (Multi-row horizontally scrollable columns)
        if (quickPicks.isNotEmpty()) {
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

                    // Chunk quick picks into columns of 4 stacked songs
                    val columns = remember(quickPicks) { quickPicks.chunked(4) }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(columns) { columnSongs ->
                            Column(
                                modifier = Modifier.width(310.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                columnSongs.forEach { song ->
                                    val isCur = currentSong?.id == song.id
                                    SongRow(
                                        song = song,
                                        isCurrent = isCur,
                                        isPlaying = isPlaying && isCur,
                                        onClick = { viewModel.playSong(song) },
                                        onMoreClick = { viewModel.toggleFavorite(song) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. LIKED SONGS RAIL
        if (likedSongs.isNotEmpty()) {
            item(key = "liked_songs_rail") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp)
                ) {
                    SectionHeader(
                        title = "Liked songs",
                        subtitle = "${likedSongs.size} tracks saved",
                        actionText = "See all",
                        onActionClick = onLikedSongsClick
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(likedSongs, key = { it.id }) { song ->
                            SquareArtworkCard(
                                title = song.title,
                                subtitle = song.artist,
                                song = song,
                                onClick = { viewModel.playSong(song) }
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
                        subtitle = "From your local library",
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
                                onClick = { viewModel.playSong(song) }
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

@Composable
fun SquareArtworkCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    song: Song? = null,
    thumbnailUrl: String? = null,
    onClick: () -> Unit
) {
    val cardWidth = 140.dp
    val artShape = RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .width(cardWidth)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(cardWidth)
                .clip(artShape)
                .background(LyroSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (song != null) {
                SongArtworkThumbnail(song = song, size = cardWidth)
            } else if (!thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
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
