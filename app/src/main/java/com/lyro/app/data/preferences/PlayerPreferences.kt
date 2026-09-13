package com.lyro.app.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlayerStyle {
    CASSETTE,
    WHEEL
}

class PlayerPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _playerStyle = MutableStateFlow(loadPlayerStyle())
    val playerStyle: StateFlow<PlayerStyle> = _playerStyle.asStateFlow()

    private fun loadPlayerStyle(): PlayerStyle {
        val saved = prefs.getString(KEY_PLAYER_STYLE, PlayerStyle.CASSETTE.name)
        return try {
            PlayerStyle.valueOf(saved ?: PlayerStyle.CASSETTE.name)
        } catch (e: Exception) {
            PlayerStyle.CASSETTE
        }
    }

    fun setPlayerStyle(style: PlayerStyle) {
        prefs.edit().putString(KEY_PLAYER_STYLE, style.name).apply()
        _playerStyle.value = style
    }

    companion object {
        private const val PREFS_NAME = "lyro_player_prefs"
        private const val KEY_PLAYER_STYLE = "player_style"
    }
}
