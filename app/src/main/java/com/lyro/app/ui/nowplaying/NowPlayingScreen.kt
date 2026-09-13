package com.lyro.app.ui.nowplaying

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.lyro.app.data.preferences.PlayerPreferences
import com.lyro.app.data.preferences.PlayerStyle

@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel,
    playerPreferences: PlayerPreferences,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerStyle by playerPreferences.playerStyle.collectAsState()

    when (playerStyle) {
        PlayerStyle.MINIMAL -> {
            MinimalPlayerScreen(
                viewModel = viewModel,
                onBackClick = onBackClick,
                modifier = modifier
            )
        }
        PlayerStyle.CASSETTE -> {
            CassettePlayerScreen(
                viewModel = viewModel,
                onBackClick = onBackClick,
                modifier = modifier
            )
        }
        PlayerStyle.WHEEL -> {
            WheelPlayerScreen(
                viewModel = viewModel,
                onBackClick = onBackClick,
                modifier = modifier
            )
        }
    }
}
