package com.lyro.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.lyro.app.core.designsystem.*
import com.lyro.app.ui.components.MiniPlayer
import com.lyro.app.ui.components.NeoBrutalAppBar
import com.lyro.app.ui.nowplaying.NowPlayingScreen
import com.lyro.app.ui.nowplaying.NowPlayingViewModel
import com.lyro.app.ui.playlists.PlaylistsScreen
import com.lyro.app.ui.settings.SettingsScreen
import com.lyro.app.ui.songs.SongsScreen
import com.lyro.app.ui.songs.SongsViewModel

enum class CurrentTab {
    TRACKS,
    MIXTAPES
}

enum class ActiveScreen {
    MAIN,
    NOW_PLAYING,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as LyroApplication
        val repository = app.musicRepository
        val playbackManager = app.playbackManager
        val playerPreferences = app.playerPreferences

        setContent {
            LyroTheme {
                val songsViewModel = remember { SongsViewModel(repository, app.onlineMusicRepository, playbackManager) }
                val nowPlayingViewModel = remember { NowPlayingViewModel(playbackManager, repository) }

                val currentSong by playbackManager.currentSong.collectAsState()
                val isPlaying by playbackManager.isPlaying.collectAsState()
                val currentPosition by playbackManager.currentPosition.collectAsState()
                val duration by playbackManager.duration.collectAsState()

                var currentTab by remember { mutableStateOf(CurrentTab.TRACKS) }
                var activeScreen by remember { mutableStateOf(ActiveScreen.MAIN) }

                // Permission state
                val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

                var hasPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            audioPermission
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasPermission = isGranted
                    if (isGranted) {
                        songsViewModel.loadSongs()
                    }
                }

                LaunchedEffect(Unit) {
                    if (!hasPermission) {
                        permissionLauncher.launch(audioPermission)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NeoBgLight
                ) {
                    when (activeScreen) {
                        ActiveScreen.NOW_PLAYING -> {
                            NowPlayingScreen(
                                viewModel = nowPlayingViewModel,
                                playerPreferences = playerPreferences,
                                onBackClick = { activeScreen = ActiveScreen.MAIN }
                            )
                        }

                        ActiveScreen.SETTINGS -> {
                            SettingsScreen(
                                viewModel = songsViewModel,
                                playerPreferences = playerPreferences,
                                onBackClick = { activeScreen = ActiveScreen.MAIN }
                            )
                        }

                        ActiveScreen.MAIN -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Top App Bar
                                    NeoBrutalAppBar(
                                        title = "LYRO",
                                        onSettingsClick = { activeScreen = ActiveScreen.SETTINGS }
                                    )

                                    // Body content
                                    Box(modifier = Modifier.weight(1f)) {
                                        when (currentTab) {
                                            CurrentTab.TRACKS -> {
                                                SongsScreen(
                                                    viewModel = songsViewModel,
                                                    hasPermission = hasPermission,
                                                    onRequestPermission = {
                                                        permissionLauncher.launch(audioPermission)
                                                    }
                                                )
                                            }

                                            CurrentTab.MIXTAPES -> {
                                                PlaylistsScreen(
                                                    viewModel = songsViewModel,
                                                    onPlaylistClick = { _ ->
                                                        currentTab = CurrentTab.TRACKS
                                                    },
                                                    onLikedSongsClick = {
                                                        songsViewModel.setFavoritesFilter(true)
                                                        currentTab = CurrentTab.TRACKS
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Bottom floating container: MiniPlayer + Bottom Navigation
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                ) {
                                    // Persistent Mini Player
                                    MiniPlayer(
                                        song = currentSong,
                                        isPlaying = isPlaying,
                                        currentPosition = currentPosition,
                                        duration = duration,
                                        onPlayPauseClick = { playbackManager.togglePlayPause() },
                                        onNextClick = { playbackManager.skipNext() },
                                        onExpandClick = { activeScreen = ActiveScreen.NOW_PLAYING }
                                    )

                                    // Neo-Brutalist Bottom Navigation Bar
                                    NeoBottomNavBar(
                                        currentTab = currentTab,
                                        onTabSelected = { currentTab = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NeoBottomNavBar(
    currentTab: CurrentTab,
    onTabSelected: (CurrentTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeoBgLight)
            .border(width = 2.dp, color = NeoBlack)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tracks Tab Button
        NeoNavButton(
            label = "TRACKS",
            icon = Icons.Default.MusicNote,
            selected = currentTab == CurrentTab.TRACKS,
            activeColor = NeoAcidGreen,
            onClick = { onTabSelected(CurrentTab.TRACKS) }
        )

        // Mixtapes Tab Button
        NeoNavButton(
            label = "MIXTAPES",
            icon = Icons.Default.LibraryMusic,
            selected = currentTab == CurrentTab.MIXTAPES,
            activeColor = NeoCyberYellow,
            onClick = { onTabSelected(CurrentTab.MIXTAPES) }
        )
    }
}

@Composable
fun NeoNavButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    activeColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)

    Box(modifier = Modifier.padding(end = 2.dp, bottom = 2.dp)) {
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 2.dp, y = 2.dp)
                    .background(NeoBlack, shape)
            )
        }

        Row(
            modifier = Modifier
                .background(if (selected) activeColor else NeoBgLight, shape)
                .border(if (selected) 2.dp else 0.dp, if (selected) NeoBlack else androidx.compose.ui.graphics.Color.Transparent, shape)
                .clip(shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = NeoBlack,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                fontSize = 12.sp,
                color = NeoBlack
            )
        }
    }
}
