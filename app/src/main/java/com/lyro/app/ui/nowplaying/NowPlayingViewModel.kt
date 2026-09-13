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
import com.lyro.app.data.model.UnifiedTrack
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
    val queueTracks: StateFlow<List<PlayableTrack>> = playbackManager.queue
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
            is UnifiedTrack -> {
                if (track.isDownloaded) {
                    DownloadStatus.Completed
                } else {
                    val videoId = track.onlineVideoId
                    val status = if (videoId != null) statuses[videoId] else null
                    if (status != null) {
                        status
                    } else if (musicDownloader.isTrackDownloaded(track)) {
                        DownloadStatus.Completed
                    } else {
                        DownloadStatus.Idle
                    }
                }
            }
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

    // Unified Favorite State for currently playing track
    val isCurrentTrackFavorite: StateFlow<Boolean> = combine(
        playbackManager.currentTrack,
        repository.favoriteTracks
    ) { track, favs ->
        if (track == null) false
        else {
            val canonicalId = track.onlineVideoId?.let { "online_$it" } ?: track.id
            val videoId = track.onlineVideoId
            favs.any { fav ->
                fav.id == track.id ||
                fav.id == canonicalId ||
                (!videoId.isNullOrBlank() && fav.onlineVideoId == videoId)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun downloadCurrentTrack() {
        val track = playbackManager.currentTrack.value
        if (track is OnlineTrack) {
            viewModelScope.launch {
                musicDownloader.downloadTrack(track)
            }
        } else if (track is UnifiedTrack) {
            val vId = track.onlineVideoId
            if (vId != null) {
                val online = OnlineTrack(
                    videoId = vId,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    thumbnailUrl = track.artworkUrl,
                    isFavorite = track.isFavorite
                )
                viewModelScope.launch {
                    musicDownloader.downloadTrack(online)
                }
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

    fun toggleFavorite(track: PlayableTrack) {
        viewModelScope.launch {
            val wasFav = repository.isTrackFavorite(track)
            repository.toggleFavoriteTrack(track)
            try {
                val eventType = if (!wasFav) com.lyro.app.recommendation.model.EventType.LIKED else com.lyro.app.recommendation.model.EventType.UNLIKED
                val canonicalId = track.onlineVideoId?.let { "online_$it" } ?: track.id
                LyroApplication.instance.listeningEventRepository.recordEvent(
                    com.lyro.app.recommendation.model.ListeningEvent(
                        playbackSessionId = "favorite_toggle",
                        videoId = canonicalId,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        eventType = eventType
                    )
                )
            } catch (e: Exception) {
                // Non-blocking
            }
        }
    }

    fun toggleFavorite(song: Song) {
        toggleFavorite(com.lyro.app.data.model.LocalTrack(song = song))
    }

    // Radio State
    val isRadioActive: StateFlow<Boolean> = LyroApplication.instance.radioManager.isRadioActive
    val currentRadioSession: StateFlow<com.lyro.app.recommendation.radio.RadioSession?> = LyroApplication.instance.radioManager.currentSession

    fun stopRadio() = LyroApplication.instance.radioManager.stopRadio()

    fun startSongRadio(track: PlayableTrack) = LyroApplication.instance.radioManager.startSongRadio(track)

    // Queue Continuation (Autoplay) State & Controls
    val isLoadingMoreQueue: StateFlow<Boolean> = LyroApplication.instance.queueContinuationManager.isLoadingMore
    val queueContinuationError: StateFlow<String?> = LyroApplication.instance.queueContinuationManager.loadError

    fun ensureMoreQueueTracks() {
        LyroApplication.instance.queueContinuationManager.ensureMoreTracks()
    }

    fun retryQueueExtension() {
        LyroApplication.instance.queueContinuationManager.retry()
    }

    fun playQueueItem(index: Int) {
        playbackManager.playTrackAtIndex(index)
    }

    fun playQueueTrack(track: PlayableTrack) {
        val index = queueTracks.value.indexOfFirst { it.id == track.id }
        if (index >= 0) {
            playbackManager.playTrackAtIndex(index)
        } else {
            playbackManager.playTrack(track)
        }
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
