package com.lyro.app.service

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lyro.app.data.model.Song
import com.lyro.app.data.repository.MusicRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackManager(
    private val context: Context,
    private val musicRepository: MusicRepository
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    // Equalizer and BassBoost audio effects
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    // Reactive states
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    // Sleep Timer
    private val _sleepTimerMinutesLeft = MutableStateFlow<Int?>(null)
    val sleepTimerMinutesLeft: StateFlow<Int?> = _sleepTimerMinutesLeft.asStateFlow()
    private var sleepTimerJob: Job? = null

    // Position tracking job
    private var positionUpdateJob: Job? = null

    init {
        setupPlayerListener()
        initAudioEffects()
    }

    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) {
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = player.duration.coerceAtLeast(0L)
                } else if (playbackState == Player.STATE_ENDED) {
                    onSongEnded()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _duration.value = player.duration.coerceAtLeast(0L)
            }
        })
    }

    private fun initAudioEffects() {
        try {
            val audioSessionId = player.audioSessionId
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = true
                }
                bassBoost = BassBoost(0, audioSessionId).apply {
                    enabled = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playSong(song: Song, newQueue: List<Song>? = null) {
        if (newQueue != null && newQueue.isNotEmpty()) {
            _queue.value = newQueue
            _currentIndex.value = newQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        } else if (_queue.value.none { it.id == song.id }) {
            _queue.value = listOf(song)
            _currentIndex.value = 0
        } else {
            _currentIndex.value = _queue.value.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        }

        _currentSong.value = song

        try {
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(song.albumArtUri)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(song.contentUri)
                .setMediaMetadata(metadata)
                .build()

            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()

            coroutineScope.launch {
                musicRepository.recordPlayed(song.id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun skipNext() {
        val q = _queue.value
        if (q.isEmpty()) return

        val nextIndex = if (_isShuffle.value) {
            q.indices.random()
        } else {
            (_currentIndex.value + 1) % q.size
        }

        _currentIndex.value = nextIndex
        playSong(q[nextIndex])
    }

    fun skipPrevious() {
        val q = _queue.value
        if (q.isEmpty()) return

        // If played more than 3 seconds, restart current song
        if (player.currentPosition > 3000) {
            player.seekTo(0)
            _currentPosition.value = 0
            return
        }

        val prevIndex = if (_isShuffle.value) {
            q.indices.random()
        } else {
            if (_currentIndex.value - 1 < 0) q.size - 1 else _currentIndex.value - 1
        }

        _currentIndex.value = prevIndex
        playSong(q[prevIndex])
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun cycleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = nextMode
        player.repeatMode = nextMode
    }

    private fun onSongEnded() {
        when (_repeatMode.value) {
            Player.REPEAT_MODE_ONE -> {
                player.seekTo(0)
                player.play()
            }
            Player.REPEAT_MODE_ALL -> {
                skipNext()
            }
            else -> {
                val q = _queue.value
                if (_currentIndex.value < q.size - 1) {
                    skipNext()
                } else {
                    _isPlaying.value = false
                    stopPositionUpdates()
                }
            }
        }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = coroutineScope.launch {
            while (isActive) {
                _currentPosition.value = player.currentPosition
                delay(250)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    // Sleep Timer
    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutesLeft.value = minutes

        if (minutes != null && minutes > 0) {
            sleepTimerJob = coroutineScope.launch {
                var remaining = minutes
                while (remaining > 0) {
                    delay(60_000L)
                    remaining--
                    _sleepTimerMinutesLeft.value = remaining
                }
                // Pause playback when timer completes
                player.pause()
                _sleepTimerMinutesLeft.value = null
            }
        }
    }

    // Equalizer & Audio FX Controls
    fun getEqualizer(): Equalizer? = equalizer

    fun getBassBoost(): BassBoost? = bassBoost

    fun setBassBoostStrength(strength: Short) {
        try {
            bassBoost?.setStrength(strength)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setBandLevel(band: Short, level: Short) {
        try {
            equalizer?.setBandLevel(band, level)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun usePreset(presetIndex: Short) {
        try {
            equalizer?.usePreset(presetIndex)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        stopPositionUpdates()
        sleepTimerJob?.cancel()
        coroutineScope.cancel()
        equalizer?.release()
        bassBoost?.release()
        player.release()
    }
}
