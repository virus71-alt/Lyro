package com.lyro.app.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lyro.app.LyroApplication
import com.lyro.app.data.download.DownloadStatus
import com.lyro.app.data.download.MusicDownloader
import com.lyro.app.data.model.LocalTrack
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.data.model.Song
import com.lyro.app.data.repository.MusicRepository
import com.lyro.app.service.PlaybackManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NowPlayingViewModel(
    private val playbackManager: PlaybackManager,
    private val repository: MusicRepository,
    private val musicDownloader: MusicDownloader = LyroApplication.instance.musicDownloader
) : ViewModel() {

    val currentSong: StateFlow<Song?> = playbackManager.currentSong
    val currentTrack: StateFlow<PlayableTrack?> = playbackManager.currentTrack
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val currentPosition: StateFlow<Long> = playbackManager.currentPosition
    val duration: StateFlow<Long> = playbackManager.duration
    val queue: StateFlow<List<Song>> = playbackManager.songQueue
    val currentIndex: StateFlow<Int> = playbackManager.currentIndex
    val isShuffle: StateFlow<Boolean> = playbackManager.isShuffle
    val repeatMode: StateFlow<Int> = playbackManager.repeatMode
    val sleepTimerMinutesLeft: StateFlow<Int?> = playbackManager.sleepTimerMinutesLeft

    val currentDownloadStatus: StateFlow<DownloadStatus> = combine(
        playbackManager.currentTrack,
        musicDownloader.downloadStatuses,
        repository.allSongs
    ) { track, statuses, _ ->
        when (track) {
            null -> DownloadStatus.Idle
            is LocalTrack -> DownloadStatus.Completed
            is OnlineTrack -> {
                val status = statuses[track.videoId]
                if (status != null) {
                    status
                } else if (musicDownloader.isTrackDownloaded(track)) {
                    DownloadStatus.Completed
                } else {
                    DownloadStatus.Idle
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadStatus.Idle)

    fun downloadCurrentTrack() {
        val track = playbackManager.currentTrack.value
        if (track is OnlineTrack) {
            viewModelScope.launch {
                musicDownloader.downloadTrack(track)
            }
        }
    }

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

    fun playQueueItem(index: Int) {
        playbackManager.playTrackAtIndex(index)
    }

    fun playQueueItem(song: Song) {
        val index = queue.value.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            playbackManager.playTrackAtIndex(index)
        } else {
            playbackManager.playSong(song)
        }
    }
}
