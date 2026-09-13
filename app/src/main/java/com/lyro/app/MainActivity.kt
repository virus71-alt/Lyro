package com.lyro.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lyro.app.core.designsystem.LyroBackground
import com.lyro.app.core.designsystem.LyroTheme
import com.lyro.app.ui.components.MiniPlayer
import com.lyro.app.ui.explore.ExploreScreen
import com.lyro.app.ui.home.HomeScreen
import com.lyro.app.ui.library.LibraryScreen
import com.lyro.app.ui.navigation.LyroBottomNavigation
import com.lyro.app.ui.navigation.MainDestination
import com.lyro.app.ui.navigation.OverlayScreen
import com.lyro.app.ui.nowplaying.NowPlayingScreen
import com.lyro.app.ui.nowplaying.NowPlayingViewModel
import com.lyro.app.ui.settings.SettingsScreen
import com.lyro.app.ui.songs.SongsViewModel

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

                // Primary Navigation & Overlay States
                var currentDestination by rememberSaveable { mutableStateOf(MainDestination.HOME) }
                var activeOverlay by rememberSaveable { mutableStateOf(OverlayScreen.NONE) }

                // Preserved scroll states across bottom destination switches
                val homeListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
                val exploreListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
                val libraryListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

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

                val isHapticsEnabled by playerPreferences.isHapticsEnabled.collectAsState()
                val localView = androidx.compose.ui.platform.LocalView.current
                val hapticsController = remember(localView, isHapticsEnabled) {
                    com.lyro.app.core.haptics.LyroHapticsController(localView) { isHapticsEnabled }
                }

                // Dynamic bottom padding calculation:
                // Prevents list content from being hidden behind MiniPlayer and BottomNavigation
                val bottomPadding = if (currentSong != null) 148.dp else 76.dp
                val contentPadding = PaddingValues(bottom = bottomPadding)

                // Predictable Android Back Navigation:
                // 1. Overlay screens (Now Playing, Settings) dismiss back to the exact active tab
                // 2. Secondary tabs (Explore, Library) return back to Home root
                // 3. Home root exits activity
                BackHandler(enabled = activeOverlay != OverlayScreen.NONE) {
                    hapticsController.click()
                    activeOverlay = OverlayScreen.NONE
                }

                BackHandler(enabled = activeOverlay == OverlayScreen.NONE && currentDestination != MainDestination.HOME) {
                    hapticsController.selection()
                    currentDestination = MainDestination.HOME
                }

                CompositionLocalProvider(com.lyro.app.core.haptics.LocalLyroHaptics provides hapticsController) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = LyroBackground
                    ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 1. Destination Content with Restrained Crossfade
                        Crossfade(
                            targetState = currentDestination,
                            animationSpec = tween(durationMillis = 180),
                            label = "MainDestinationCrossfade"
                        ) { destination ->
                            when (destination) {
                                MainDestination.HOME -> {
                                    HomeScreen(
                                        viewModel = songsViewModel,
                                        listState = homeListState,
                                        contentPadding = contentPadding,
                                        onSearchClick = {
                                            currentDestination = MainDestination.EXPLORE
                                        },
                                        onSettingsClick = {
                                            activeOverlay = OverlayScreen.SETTINGS
                                        },
                                        onLikedSongsClick = {
                                            currentDestination = MainDestination.LIBRARY
                                        },
                                        onPlaylistClick = { _ ->
                                            currentDestination = MainDestination.LIBRARY
                                        }
                                    )
                                }

                                MainDestination.EXPLORE -> {
                                    ExploreScreen(
                                        viewModel = songsViewModel,
                                        listState = exploreListState,
                                        contentPadding = contentPadding
                                    )
                                }

                                MainDestination.LIBRARY -> {
                                    LibraryScreen(
                                        viewModel = songsViewModel,
                                        listState = libraryListState,
                                        contentPadding = contentPadding,
                                        hasPermission = hasPermission,
                                        onRequestPermission = {
                                            permissionLauncher.launch(audioPermission)
                                        },
                                        onPlaylistClick = { _ -> }
                                    )
                                }
                            }
                        }

                        // 2. Persistent Floating Bottom Container: MiniPlayer + LyroBottomNavigation
                        // Visible across all 3 destinations; hidden when an overlay screen is active
                        AnimatedVisibility(
                            visible = activeOverlay == OverlayScreen.NONE,
                            enter = fadeIn(animationSpec = tween(150)),
                            exit = fadeOut(animationSpec = tween(150)),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                            ) {
                                if (currentSong != null) {
                                    MiniPlayer(
                                        song = currentSong,
                                        isPlaying = isPlaying,
                                        currentPosition = currentPosition,
                                        duration = duration,
                                        onPlayPauseClick = { playbackManager.togglePlayPause() },
                                        onNextClick = { playbackManager.skipNext() },
                                        onExpandClick = { activeOverlay = OverlayScreen.NOW_PLAYING }
                                    )
                                }

                                LyroBottomNavigation(
                                    currentDestination = currentDestination,
                                    onDestinationSelected = { selected ->
                                        currentDestination = selected
                                    }
                                )
                            }
                        }

                        // 3. Fullscreen Overlay: Now Playing Screen (Vertical Slide + Fade)
                        AnimatedVisibility(
                            visible = activeOverlay == OverlayScreen.NOW_PLAYING,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(280)
                            ) + fadeIn(tween(280)),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(240)
                            ) + fadeOut(tween(240))
                        ) {
                            NowPlayingScreen(
                                viewModel = nowPlayingViewModel,
                                playerPreferences = playerPreferences,
                                onBackClick = { activeOverlay = OverlayScreen.NONE }
                            )
                        }

                        // 4. Fullscreen Overlay: Settings Screen (Vertical Slide + Fade)
                        AnimatedVisibility(
                            visible = activeOverlay == OverlayScreen.SETTINGS,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(280)
                            ) + fadeIn(tween(280)),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(240)
                            ) + fadeOut(tween(240))
                        ) {
                            SettingsScreen(
                                viewModel = songsViewModel,
                                playerPreferences = playerPreferences,
                                onBackClick = { activeOverlay = OverlayScreen.NONE }
                            )
                        }
                    }
                }
                }
            }
        }
    }
}
