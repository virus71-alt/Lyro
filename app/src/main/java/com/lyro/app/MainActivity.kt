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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
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
import com.lyro.app.ui.explore.ExploreScreen
import com.lyro.app.ui.home.HomeScreen
import com.lyro.app.ui.library.LibraryScreen
import com.lyro.app.ui.nowplaying.NowPlayingScreen
import com.lyro.app.ui.nowplaying.NowPlayingViewModel
import com.lyro.app.ui.settings.SettingsScreen
import com.lyro.app.ui.songs.SongsViewModel

enum class CurrentTab {
    HOME,
    EXPLORE,
    LIBRARY
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
                val songsViewModel = remember {
                    SongsViewModel(
                        repository = repository,
                        onlineRepository = app.onlineMusicRepository,
                        playbackManager = playbackManager,
                        musicDownloader = app.musicDownloader
                    )
                }
                val nowPlayingViewModel = remember {
                    NowPlayingViewModel(playbackManager, repository, app.musicDownloader)
                }

                val currentSong by playbackManager.currentSong.collectAsState()
                val isPlaying by playbackManager.isPlaying.collectAsState()
                val currentPosition by playbackManager.currentPosition.collectAsState()
                val duration by playbackManager.duration.collectAsState()

                var currentTab by remember { mutableStateOf(CurrentTab.HOME) }
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
                    color = LyroBackground
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
                                // Body Content (Tab-based primary navigation)
                                Box(modifier = Modifier.fillMaxSize()) {
                                    when (currentTab) {
                                        CurrentTab.HOME -> {
                                            HomeScreen(
                                                viewModel = songsViewModel,
                                                onSearchClick = {
                                                    currentTab = CurrentTab.EXPLORE
                                                },
                                                onSettingsClick = {
                                                    activeScreen = ActiveScreen.SETTINGS
                                                },
                                                onLikedSongsClick = {
                                                    currentTab = CurrentTab.LIBRARY
                                                },
                                                onPlaylistClick = { _ ->
                                                    currentTab = CurrentTab.LIBRARY
                                                }
                                            )
                                        }

                                        CurrentTab.EXPLORE -> {
                                            ExploreScreen(
                                                viewModel = songsViewModel
                                            )
                                        }

                                        CurrentTab.LIBRARY -> {
                                            LibraryScreen(
                                                viewModel = songsViewModel,
                                                hasPermission = hasPermission,
                                                onRequestPermission = {
                                                    permissionLauncher.launch(audioPermission)
                                                },
                                                onPlaylistClick = { _ -> }
                                            )
                                        }
                                    }
                                }

                                // Persistent Floating Bottom Container: MiniPlayer + Bottom Navigation
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

                                    // Minimal Material-Style Bottom Navigation Bar
                                    LyroBottomNavBar(
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
fun LyroBottomNavBar(
    currentTab: CurrentTab,
    onTabSelected: (CurrentTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LyroBackground)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LyroNavButton(
            label = "Home",
            icon = Icons.Default.Home,
            selected = currentTab == CurrentTab.HOME,
            onClick = { onTabSelected(CurrentTab.HOME) }
        )

        LyroNavButton(
            label = "Explore",
            icon = Icons.Default.Explore,
            selected = currentTab == CurrentTab.EXPLORE,
            onClick = { onTabSelected(CurrentTab.EXPLORE) }
        )

        LyroNavButton(
            label = "Library",
            icon = Icons.Default.LibraryMusic,
            selected = currentTab == CurrentTab.LIBRARY,
            onClick = { onTabSelected(CurrentTab.LIBRARY) }
        )
    }
}

@Composable
fun LyroNavButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val selectedColor = LyroTextPrimary
    val unselectedColor = LyroTextMuted

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) LyroTextPrimary else unselectedColor,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 11.sp,
            color = if (selected) selectedColor else unselectedColor
        )
    }
}
