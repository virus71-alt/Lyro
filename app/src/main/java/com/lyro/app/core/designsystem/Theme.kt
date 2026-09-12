package com.lyro.app.core.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = NeoAcidGreen,
    secondary = NeoCyberYellow,
    tertiary = NeoHotPink,
    background = NeoBgLight,
    surface = NeoWhite,
    onPrimary = NeoBlack,
    onSecondary = NeoBlack,
    onTertiary = NeoWhite,
    onBackground = NeoBlack,
    onSurface = NeoBlack
)

private val DarkColorScheme = darkColorScheme(
    primary = NeoAcidGreen,
    secondary = NeoCyberYellow,
    tertiary = NeoHotPink,
    background = NeoBgDark,
    surface = NeoSurfaceDark,
    onPrimary = NeoBlack,
    onSecondary = NeoBlack,
    onTertiary = NeoWhite,
    onBackground = NeoWhite,
    onSurface = NeoWhite
)

@Composable
fun LyroTheme(
    darkTheme: Boolean = false, // Brutalist light mode is default iconic style
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NeoTypography,
        content = content
    )
}
