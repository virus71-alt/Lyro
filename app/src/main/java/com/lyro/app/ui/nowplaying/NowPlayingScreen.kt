package com.lyro.app.ui.nowplaying

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.lyro.app.core.designsystem.LyroBackground
import com.lyro.app.data.preferences.PlayerPreferences
import com.lyro.app.data.preferences.PlayerStyle

@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel,
    playerPreferences: PlayerPreferences,
    playerSheetState: PlayerSheetState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerStyle by playerPreferences.playerStyle.collectAsState()

    ExpandablePlayerContainer(
        sheetState = playerSheetState,
        onCollapse = onBackClick,
        modifier = modifier
    ) {
        when (playerStyle) {
            PlayerStyle.MINIMAL -> {
                MinimalPlayerScreen(
                    viewModel = viewModel,
                    onBackClick = onBackClick
                )
            }
            PlayerStyle.CASSETTE -> {
                CassettePlayerScreen(
                    viewModel = viewModel,
                    onBackClick = onBackClick
                )
            }
            PlayerStyle.WHEEL -> {
                WheelPlayerScreen(
                    viewModel = viewModel,
                    onBackClick = onBackClick
                )
            }
        }
    }
}
