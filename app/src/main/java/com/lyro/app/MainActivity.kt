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
import com.lyro.app.ui.nowplaying.PlayerSheetState
import com.lyro.app.ui.nowplaying.PlayerSheetValue
import com.lyro.app.ui.nowplaying.rememberPlayerSheetState
import com.lyro.app.ui.settings.SettingsScreen
import com.lyro.app.ui.songs.SongsViewModel
import kotlinx.coroutines.launch

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
                val currentTrack by playbackManager.currentTrack.collectAsState()
                val hasTrack = currentTrack != null || currentSong != null

                // Primary Navigation & Overlay States
                var currentDestination by rememberSaveable { mutableStateOf(MainDestination.HOME) }
                var activeOverlay by rememberSaveable { mutableStateOf(OverlayScreen.NONE) }

                // Preserved scroll states across bottom destination switches
                val homeListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
                val exploreListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
                val libraryListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

                val playerSheetState = rememberPlayerSheetState(
                    initialValue = PlayerSheetValue.COLLAPSED
                )
                val coroutineScope = rememberCoroutineScope()

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

                // Automatically navigate to full Now Playing whenever a song/track is tapped
                LaunchedEffect(Unit) {
                    songsViewModel.openNowPlayingEvent.collect {
                        if (activeOverlay != OverlayScreen.NONE) {
                            activeOverlay = OverlayScreen.NONE
                        }
                        playerSheetState.expand()
                    }
                }

                val isHapticsEnabled by playerPreferences.isHapticsEnabled.collectAsState()
                val localView = androidx.compose.ui.platform.LocalView.current
                val hapticsController = remember(localView, isHapticsEnabled) {
                    com.lyro.app.core.haptics.LyroHapticsController(localView) { isHapticsEnabled }
                }

                // Dynamic bottom padding calculation:
                // Prevents list content from being hidden behind MiniPlayer and BottomNavigation
                val bottomPadding = if (hasTrack) 148.dp else 76.dp
                val contentPadding = PaddingValues(bottom = bottomPadding)

                // Predictable Android Back Navigation:
                // 1. Gesture-driven Now Playing dismisses with smooth animated collapse
                BackHandler(enabled = playerSheetState.isVisible && activeOverlay == OverlayScreen.NONE) {
                    hapticsController.click()
                    coroutineScope.launch {
                        playerSheetState.collapse {
                            activeOverlay = OverlayScreen.NONE
                        }
                    }
                }

                // 2. Overlay screens (Settings) dismiss back to the exact active tab
                BackHandler(enabled = activeOverlay != OverlayScreen.NONE) {
                    hapticsController.click()
                    activeOverlay = OverlayScreen.NONE
                }

                // 3. Secondary tabs (Explore, Library) return back to Home root
                BackHandler(enabled = activeOverlay == OverlayScreen.NONE && !playerSheetState.isVisible && currentDestination != MainDestination.HOME) {
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
                                        onPlaylistClick = { playlist ->
                                            songsViewModel.playPlaylist(playlist)
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
                        // Visible across all destinations underneath Now Playing; hidden when Settings overlay is active
                        AnimatedVisibility(
                            visible = activeOverlay != OverlayScreen.SETTINGS,
                            enter = fadeIn(animationSpec = tween(150)),
                            exit = fadeOut(animationSpec = tween(150)),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                            ) {
                                if (hasTrack) {
                                    MiniPlayer(
                                        playbackManager = playbackManager,
                                        onExpandClick = {
                                            coroutineScope.launch {
                                                playerSheetState.expand()
                                            }
                                        }
                                    )
                                }

                                val isHomeRefreshing by songsViewModel.isHomeRefreshing.collectAsState()
                                LyroBottomNavigation(
                                    currentDestination = currentDestination,
                                    isHomeRefreshing = isHomeRefreshing,
                                    onDestinationSelected = { selected ->
                                        currentDestination = selected
                                    },
                                    onCurrentDestinationReselected = { reselected ->
                                        if (reselected == MainDestination.HOME) {
                                            songsViewModel.refreshHome()
                                        }
                                    }
                                )
                            }
                        }

                        // 3. Gesture-Driven Expandable Now Playing Screen (Real-time swipe-down collapse)
                        if (playerSheetState.isVisible && hasTrack && activeOverlay != OverlayScreen.SETTINGS) {
                            NowPlayingScreen(
                                viewModel = nowPlayingViewModel,
                                playerPreferences = playerPreferences,
                                playerSheetState = playerSheetState,
                                onBackClick = {
                                    coroutineScope.launch {
                                        playerSheetState.collapse {
                                            activeOverlay = OverlayScreen.NONE
                                        }
                                    }
                                }
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
