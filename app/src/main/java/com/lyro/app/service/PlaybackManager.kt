package com.lyro.app.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.datasource.HttpDataSource
import com.lyro.app.core.artwork.ArtworkUtils
import com.lyro.app.data.model.LocalTrack
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.data.model.Song
import com.lyro.app.data.model.toLocalTrack
import com.lyro.app.data.repository.MusicRepository
import com.lyro.app.streaming.ResolvedStream
import com.lyro.app.streaming.StreamResolver
import com.lyro.app.streaming.youtube.YouTubeClientProfile
import com.lyro.app.streaming.youtube.YouTubeStreamResolver
import com.lyro.app.core.matcher.LocalMediaIndex
import com.lyro.app.data.model.UnifiedTrack
import com.lyro.app.data.model.toSong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PlaybackManager(
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val streamResolver: StreamResolver = YouTubeStreamResolver(),
    private val localMediaIndex: LocalMediaIndex? = null,
    private val playbackSourceResolver: PlaybackSourceResolver? = null
) {
    companion object {
        private const val TAG = "LyroPlayback"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val sourceResolver: PlaybackSourceResolver by lazy {
        playbackSourceResolver ?: PlaybackSourceResolver(
            context,
            localMediaIndex ?: LocalMediaIndex()
        )
    }

    // Authoritative MediaController connected to LyroMediaService's single ExoPlayer
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // Reactive states
    private val _currentTrack = MutableStateFlow<PlayableTrack?>(null)
    val currentTrack: StateFlow<PlayableTrack?> = _currentTrack.asStateFlow()

    private val _currentResolvedStream = MutableStateFlow<ResolvedStream?>(null)
    val currentResolvedStream: StateFlow<ResolvedStream?> = _currentResolvedStream.asStateFlow()

    private val failedProfilesForCurrentTrack = mutableSetOf<String>()

    // Backward compatibility for existing UI referencing Song
    val currentSong: StateFlow<Song?> = _currentTrack.map { track ->
        when (track) {
            is LocalTrack -> track.song
            is UnifiedTrack -> track.toSong()
            is OnlineTrack -> {
                val highResUrl = ArtworkUtils.getHighResArtworkUrl(track.thumbnailUrl) ?: track.thumbnailUrl
                Song(
                    id = track.videoId.hashCode().toLong(),
                    title = track.title,
                    artist = track.artist,
                    album = track.album ?: "YouTube Music",
                    albumId = 0L,
                    duration = track.durationMs,
                    contentUriString = highResUrl ?: "",
                    albumArtUriString = highResUrl,
                    size = 0L,
                    dateAdded = 0L,
                    isFavorite = track.isFavorite
                )
            }
            null -> null
        }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, null)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isResolvingStream = MutableStateFlow(false)
    val isResolvingStream: StateFlow<Boolean> = _isResolvingStream.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    fun clearPlaybackError() {
        _playbackError.value = null
    }

    fun clearCurrentTrack() {
        withController { it.stop() }
        _currentTrack.value = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        _playbackError.value = null
        _queue.value = emptyList()
        stopPositionUpdates()
    }

    fun validateCurrentTrack(validSongs: List<Song>) {
        val track = _currentTrack.value
        if (track is LocalTrack) {
            val exists = validSongs.any { it.id == track.song.id }
            if (!exists) {
                clearCurrentTrack()
            }
        }
    }

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _queue = MutableStateFlow<List<PlayableTrack>>(emptyList())
    val queue: StateFlow<List<PlayableTrack>> = _queue.asStateFlow()

    // Backward-compatible queue for Song (supports LocalTrack, UnifiedTrack, and OnlineTrack)
    val songQueue: StateFlow<List<Song>> = _queue.map { list ->
        list.map { track ->
            when (track) {
                is LocalTrack -> track.song
                is UnifiedTrack -> track.toSong()
                is OnlineTrack -> {
                    val highResUrl = ArtworkUtils.getHighResArtworkUrl(track.thumbnailUrl) ?: track.thumbnailUrl
                    Song(
                        id = track.videoId.hashCode().toLong(),
                        title = track.title,
                        artist = track.artist,
                        album = track.album ?: "YouTube Music",
                        albumId = 0L,
                        duration = track.durationMs,
                        contentUriString = highResUrl ?: "",
                        albumArtUriString = highResUrl,
                        size = 0L,
                        dateAdded = 0L,
                        isFavorite = track.isFavorite
                    )
                }
            }
        }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

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

    // Retry counter for expired stream URLs
    private var expiredUrlRetryCount = 0

    // Pending action if controller is connecting
    private val pendingActions = mutableListOf<(MediaController) -> Unit>()

    init {
        initializeMediaController()
    }

    private fun initializeMediaController() {
        // Start foreground service first
        val serviceIntent = Intent(context, LyroMediaService::class.java)
        try {
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start service directly: ${e.message}")
        }

        val sessionToken = SessionToken(context, ComponentName(context, LyroMediaService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync().apply {
            addListener({
                try {
                    val controller = get()
                    mediaController = controller
                    setupPlayerListener(controller)

                    // Execute any pending actions
                    synchronized(pendingActions) {
                        pendingActions.forEach { it(controller) }
                        pendingActions.clear()
                    }
                    Log.d(TAG, "Authoritative MediaController connected to LyroMediaService successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect MediaController: ${e.message}", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        val controller = mediaController
        if (controller != null) {
            action(controller)
        } else {
            synchronized(pendingActions) {
                pendingActions.add(action)
            }
        }
    }

    private fun setupPlayerListener(player: Player) {
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
                when (playbackState) {
                    Player.STATE_READY -> {
                        _duration.value = player.duration.coerceAtLeast(0L)
                        _playbackError.value = null
                    }
                    Player.STATE_ENDED -> {
                        onTrackEnded()
                    }
                    Player.STATE_BUFFERING -> {
                        // Buffering
                    }
                    Player.STATE_IDLE -> {
                        // Idle
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _duration.value = player.duration.coerceAtLeast(0L)
            }

            override fun onPlayerError(error: PlaybackException) {
                val rootCause = error.cause
                val isHttpError = rootCause is HttpDataSource.InvalidResponseCodeException
                val stream = _currentResolvedStream.value
                val track = _currentTrack.value

                Log.e(TAG, "=== LyroPlayback Player Error ===")
                Log.e(TAG, "errorCode: ${error.errorCode} (${error.errorCodeName})")
                Log.e(TAG, "message: ${error.message}")
                Log.e(TAG, "causeClass: ${rootCause?.javaClass?.name}")
                Log.e(TAG, "causeMessage: ${rootCause?.message}")

                if (isHttpError) {
                    val httpEx = rootCause as HttpDataSource.InvalidResponseCodeException
                    Log.e(TAG, "HTTP responseCode: ${httpEx.responseCode} (${httpEx.responseMessage})")
                    Log.e(TAG, "HTTP headerFields: ${httpEx.headerFields.keys}")
                }

                if (track is OnlineTrack) {
                    Log.e(
                        TAG,
                        "Online track error: videoId=${track.videoId}, client=${stream?.clientProfileName}, itag=${stream?.itag}, mime=${stream?.mimeType}"
                    )
                }

                handlePlaybackError(error)
            }
        })
    }

    private fun handlePlaybackError(error: PlaybackException) {
        val track = _currentTrack.value
        val stream = _currentResolvedStream.value

        // If an online stream failed and we have alternative profiles to try:
        val videoId = track?.onlineVideoId ?: (track as? OnlineTrack)?.videoId
        if (videoId != null && track != null) {
            val failedClient = stream?.clientProfileName
            if (failedClient != null) {
                failedProfilesForCurrentTrack.add(failedClient)
            }

            if (failedProfilesForCurrentTrack.size < YouTubeClientProfile.ALL_PROFILES.size) {
                val lastPos = _currentPosition.value
                Log.i(
                    TAG,
                    "Retrying stream resolution for videoId=$videoId at position ${lastPos}ms (excluding failed profiles: $failedProfilesForCurrentTrack)"
                )
                playOnlineSource(
                    track = track,
                    videoId = videoId,
                    excludeProfiles = failedProfilesForCurrentTrack,
                    resumePosition = lastPos
                )
                return
            }
        }

        val rootCause = error.cause
        val detail = if (rootCause is HttpDataSource.InvalidResponseCodeException) {
            "HTTP ${rootCause.responseCode}"
        } else {
            error.localizedMessage ?: "Source error"
        }
        _playbackError.value = "Playback error: $detail"
    }
    // Playback APIs
    fun playTrack(track: PlayableTrack, newQueue: List<PlayableTrack>? = null, startIndex: Int? = null) {
        if (newQueue != null) {
            _queue.value = newQueue
            _currentIndex.value = startIndex?.takeIf { it in newQueue.indices }
                ?: newQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        } else if (_queue.value.none { it.id == track.id }) {
            _queue.value = listOf(track)
            _currentIndex.value = 0
        } else {
            _currentIndex.value = startIndex?.takeIf { it in _queue.value.indices }
                ?: _queue.value.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        }

        Log.d(
            TAG,
            "LyroPlayback: Starting playback -> queueSize=${_queue.value.size}, currentIndex=${_currentIndex.value}, title=${track.title}"
        )

        _currentTrack.value = track
        _playbackError.value = null
        expiredUrlRetryCount = 0
        failedProfilesForCurrentTrack.clear()

        dispatchPlayback(track)
    }

    /**
     * Plays a track from the existing queue without modifying, replacing, or recreating _queue.
     */
    private fun playTrackFromExistingQueue(track: PlayableTrack, index: Int) {
        val q = _queue.value
        val safeIndex = index.coerceIn(0, (q.size - 1).coerceAtLeast(0))
        _currentIndex.value = safeIndex
        _currentTrack.value = track
        _playbackError.value = null
        expiredUrlRetryCount = 0
        failedProfilesForCurrentTrack.clear()

        dispatchPlayback(track)
    }

    /**
     * Plays the track at [index] within the existing queue preserving the current queue.
     */
    fun playTrackAtIndex(index: Int) {
        val q = _queue.value
        if (index !in q.indices) return
        val track = q[index]
        Log.d(
            TAG,
            "LyroPlayback: playTrackAtIndex -> queueSize=${q.size}, index=$index, title=${track.title}"
        )
        playTrackFromExistingQueue(track, index)
    }

    // Backward-compatible for local songs
    fun playSong(song: Song, newSongQueue: List<Song>? = null, startIndex: Int? = null) {
        val localTrack = song.toLocalTrack()
        val trackQueue = newSongQueue?.map { it.toLocalTrack() }
        playTrack(localTrack, trackQueue, startIndex)
    }

    private fun dispatchPlayback(track: PlayableTrack) {
        try {
            com.lyro.app.LyroApplication.instance.recommendationEngine.startPlaybackSession(track, isManual = true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start recommendation playback session: ${e.message}")
        }
        val source = sourceResolver.resolve(track)
        when (source) {
            is PlaybackSource.Local -> {
                _currentResolvedStream.value = null
                playLocalSource(track, source.uri, source.localSong)
            }
            is PlaybackSource.Online -> {
                playOnlineSource(track, source.videoId)
            }
            is PlaybackSource.Unavailable -> {
                Log.e(TAG, "Cannot play track: ${source.reason}")
                _playbackError.value = source.reason
                _isPlaying.value = false
            }
        }
    }

    private fun playLocalSource(track: PlayableTrack, uri: Uri, matchedSong: Song?) {
        withController { controller ->
            val metadata = buildMediaMetadata(track)
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaId(track.id)
                .setMediaMetadata(metadata)
                .build()

            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()

            coroutineScope.launch {
                val songId = matchedSong?.id ?: (track as? LocalTrack)?.song?.id ?: (track as? UnifiedTrack)?.localSong?.id
                if (songId != null) {
                    musicRepository.recordPlayed(songId)
                }
            }
        }
    }

    fun playLocalTrack(track: LocalTrack) {
        dispatchPlayback(track)
    }

    fun playOnlineTrack(
        track: OnlineTrack,
        excludeProfiles: Set<String> = emptySet(),
        resumePosition: Long = 0L
    ) {
        playOnlineSource(track, track.videoId, excludeProfiles, resumePosition)
    }

    private fun playOnlineSource(
        track: PlayableTrack,
        videoId: String,
        excludeProfiles: Set<String> = emptySet(),
        resumePosition: Long = 0L
    ) {
        _playbackError.value = null
        _isResolvingStream.value = true
        coroutineScope.launch {
            Log.d(TAG, "Resolving stream for track: videoId=$videoId, title=${track.title}, excludedProfiles=$excludeProfiles")
            val result = streamResolver.resolve(videoId, excludeProfiles = excludeProfiles)
            _isResolvingStream.value = false

            result.onSuccess { stream ->
                _currentResolvedStream.value = stream
                LyroMediaService.setPlaybackHeaders(stream.requestHeaders)

                withController { controller ->
                    val metadata = buildMediaMetadata(track)
                    val containerMime = stream.mimeType?.substringBefore(";")?.trim()

                    val mediaItemBuilder = MediaItem.Builder()
                        .setUri(stream.url)
                        .setMediaId(videoId)
                        .setMediaMetadata(metadata)

                    if (!containerMime.isNullOrBlank()) {
                        mediaItemBuilder.setMimeType(containerMime)
                    }

                    val mediaItem = mediaItemBuilder.build()
                    if (resumePosition > 0L) {
                        controller.setMediaItem(mediaItem, resumePosition)
                    } else {
                        controller.setMediaItem(mediaItem)
                    }
                    controller.prepare()
                    controller.play()
                    Log.d(
                        TAG,
                        "Started playback of online stream: videoId=$videoId, client=${stream.clientProfileName}, itag=${stream.itag}, mime=$containerMime, resumePos=${resumePosition}ms"
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to resolve online track: ${error.message}", error)
                _playbackError.value = "Could not stream \"${track.title}\": ${error.message}"
            }
        }
    }

    private fun buildMediaMetadata(track: PlayableTrack): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)

        track.album?.let { builder.setAlbumTitle(it) }
        track.artworkUriString?.let {
            builder.setArtworkUri(Uri.parse(it))
        }
        return builder.build()
    }

    fun togglePlayPause() {
        withController { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                if (controller.playbackState == Player.STATE_ENDED) {
                    controller.seekTo(0)
                }
                controller.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        withController { controller ->
            controller.seekTo(positionMs)
            _currentPosition.value = positionMs
        }
    }

    fun skipNext(isManual: Boolean = true) {
        if (isManual) {
            try {
                com.lyro.app.LyroApplication.instance.recommendationEngine.activeSession?.let { session ->
                    com.lyro.app.LyroApplication.instance.recommendationEngine.onManualSkip(session)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to report manual skip: ${e.message}")
            }
        }
        val q = _queue.value
        if (q.isEmpty()) return

        val curIdx = _currentIndex.value
        val curTrack = q.getOrNull(curIdx)

        val nextIndex = if (_isShuffle.value) {
            val candidates = q.indices.filter { it != curIdx }
            if (candidates.isNotEmpty()) candidates.random() else curIdx
        } else {
            when (_repeatMode.value) {
                Player.REPEAT_MODE_ONE -> curIdx
                Player.REPEAT_MODE_ALL -> (curIdx + 1) % q.size
                else -> { // Player.REPEAT_MODE_OFF
                    if (curIdx < q.size - 1) curIdx + 1 else -1
                }
            }
        }

        if (nextIndex == -1) {
            Log.d(
                TAG,
                "LyroPlayback: End of queue reached (repeat OFF). queueSize=${q.size}, currentIndex=$curIdx, current=${curTrack?.title}"
            )
            return
        }

        val nextTrack = q[nextIndex]
        Log.d(
            TAG,
            "LyroPlayback: Next triggered -> queueSize=${q.size}, currentIndex=$curIdx, current=${curTrack?.title}, nextIndex=$nextIndex, next=${nextTrack.title}"
        )

        playTrackFromExistingQueue(nextTrack, nextIndex)
    }

    fun skipPrevious() {
        val q = _queue.value
        if (q.isEmpty()) return

        val pos = _currentPosition.value
        if (pos > 3000) {
            seekTo(0)
            return
        }

        val curIdx = _currentIndex.value
        val curTrack = q.getOrNull(curIdx)

        val prevIndex = if (_isShuffle.value) {
            val candidates = q.indices.filter { it != curIdx }
            if (candidates.isNotEmpty()) candidates.random() else curIdx
        } else {
            when (_repeatMode.value) {
                Player.REPEAT_MODE_ONE -> curIdx
                Player.REPEAT_MODE_ALL -> if (curIdx - 1 < 0) q.size - 1 else curIdx - 1
                else -> { // Player.REPEAT_MODE_OFF
                    if (curIdx > 0) curIdx - 1 else 0
                }
            }
        }

        val prevTrack = q[prevIndex]
        Log.d(
            TAG,
            "LyroPlayback: Previous triggered -> queueSize=${q.size}, currentIndex=$curIdx, current=${curTrack?.title}, prevIndex=$prevIndex, prev=${prevTrack.title}"
        )

        playTrackFromExistingQueue(prevTrack, prevIndex)
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
        withController { controller ->
            controller.shuffleModeEnabled = _isShuffle.value
        }
    }

    fun cycleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = nextMode
        withController { controller ->
            controller.repeatMode = nextMode
        }
    }

    private fun onTrackEnded() {
        try {
            com.lyro.app.LyroApplication.instance.recommendationEngine.activeSession?.let { session ->
                com.lyro.app.LyroApplication.instance.recommendationEngine.onTrackCompleted(session)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to report track completion: ${e.message}")
        }
        when (_repeatMode.value) {
            Player.REPEAT_MODE_ONE -> {
                seekTo(0)
                withController { it.play() }
            }
            Player.REPEAT_MODE_ALL -> {
                skipNext(isManual = false)
            }
            else -> {
                val q = _queue.value
                if (_currentIndex.value < q.size - 1) {
                    skipNext(isManual = false)
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
                mediaController?.let { controller ->
                    val pos = controller.currentPosition
                    _currentPosition.value = pos
                    val dur = _duration.value
                    if (dur > 0L) {
                        try {
                            com.lyro.app.LyroApplication.instance.recommendationEngine.activeSession?.let { session ->
                                com.lyro.app.LyroApplication.instance.recommendationEngine.onPlaybackProgress(session, pos, dur)
                            }
                        } catch (e: Exception) {
                            // Non-blocking
                        }
                    }
                }
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
                withController { it.pause() }
                _sleepTimerMinutesLeft.value = null
            }
        }
    }

    fun release() {
        stopPositionUpdates()
        sleepTimerJob?.cancel()
        coroutineScope.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }
}
