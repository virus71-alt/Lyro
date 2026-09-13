package com.lyro.app.ui.nowplaying

import java.util.Locale

object NowPlayingUtils {
    fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }
}
