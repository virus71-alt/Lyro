package com.lyro.app.core.designsystem

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = LyroAccent,
    onPrimary = Color.Black,
    primaryContainer = LyroSurfaceHighlight,
    onPrimaryContainer = LyroTextPrimary,
    secondary = LyroTextSecondary,
    onSecondary = LyroBackground,
    background = LyroBackground,
    onBackground = LyroTextPrimary,
    surface = LyroSurface,
    onSurface = LyroTextPrimary,
    surfaceVariant = LyroSurfaceElevated,
    onSurfaceVariant = LyroTextSecondary,
    outline = LyroDivider,
    error = LyroError,
    onError = Color.White
)

@Composable
fun LyroTheme(
    darkTheme: Boolean = true, // Minimal dark mode is the standard design
    content: @Composable () -> Unit
) {
    // Standard dark-first color scheme; prepared for light scheme extension
    val colorScheme = if (darkTheme) DarkColorScheme else DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LyroTypography,
        content = content
    )
}
