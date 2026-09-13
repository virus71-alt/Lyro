package com.lyro.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Primary navigation destinations for Lyro's bottom navigation bar.
 */
enum class MainDestination(
    val title: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    HOME(
        title = "Home",
        icon = Icons.Default.Home,
        contentDescription = "Home - Personalized music feed, recommendations, and recent activity"
    ),
    EXPLORE(
        title = "Explore",
        icon = Icons.Default.Explore,
        contentDescription = "Explore - Search, trending hits, and online music discovery"
    ),
    LIBRARY(
        title = "Library",
        icon = Icons.Default.LibraryMusic,
        contentDescription = "Library - Your playlists, liked songs, and local device collection"
    )
}

/**
 * Fullscreen overlay screens that temporarily cover the main destination
 * without resetting or altering bottom navigation state.
 */
enum class OverlayScreen {
    NONE,
    NOW_PLAYING,
    SETTINGS
}
