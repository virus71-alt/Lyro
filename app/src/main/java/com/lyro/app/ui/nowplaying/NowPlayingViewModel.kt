package com.lyro.app.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lyro.app.data.model.Song
import com.lyro.app.data.repository.MusicRepository
import com.lyro.app.service.PlaybackManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NowPlayingViewModel(
    private val playbackManager: PlaybackManager,
    private val repository: MusicRepository
) : ViewModel() {

    val currentSong: StateFlow<Song?> = playbackManager.currentSong
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val currentPosition: StateFlow<Long> = playbackManager.currentPosition
    val duration: StateFlow<Long> = playbackManager.duration
    val queue: StateFlow<List<Song>> = playbackManager.songQueue
    val currentIndex: StateFlow<Int> = playbackManager.currentIndex
    val isShuffle: StateFlow<Boolean> = playbackManager.isShuffle
    val repeatMode: StateFlow<Int> = playbackManager.repeatMode
    val sleepTimerMinutesLeft: StateFlow<Int?> = playbackManager.sleepTimerMinutesLeft

    fun togglePlayPause() = playbackManager.togglePlayPause()

    fun seekTo(positionMs: Long) = playbackManager.seekTo(positionMs)

    fun skipNext() = playbackManager.skipNext()

    fun skipPrevious() = playbackManager.skipPrevious()

    fun toggleShuffle() = playbackManager.toggleShuffle()

    fun cycleRepeatMode() = playbackManager.cycleRepeatMode()

    fun setSleepTimer(minutes: Int?) = playbackManager.setSleepTimer(minutes)

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song)
        }
    }

    fun playQueueItem(song: Song) {
        playbackManager.playSong(song)
    }
}
