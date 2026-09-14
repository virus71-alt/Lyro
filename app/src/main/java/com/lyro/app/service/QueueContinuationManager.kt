package com.lyro.app.service

import android.util.Log
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.data.repository.MusicRepository
import com.lyro.app.recommendation.RecommendationConfig
import com.lyro.app.recommendation.data.TasteProfileRepository
import com.lyro.app.recommendation.engine.CandidateGenerator
import com.lyro.app.recommendation.engine.RecommendationRanker
import com.lyro.app.recommendation.model.RecommendationContext
import com.lyro.app.recommendation.radio.RadioManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages continuous autoplay and infinite playing queue extension when Lyro Radio is not active.
 * Fetches personalized batches of 15 tracks based on current playback, queue context,
 * and user TasteProfile when scrolling near bottom or approaching end of playback.
 */
class QueueContinuationManager(
    private val playbackManager: PlaybackManager,
    private val candidateGenerator: CandidateGenerator,
    private val ranker: RecommendationRanker,
    private val tasteProfileRepository: TasteProfileRepository,
    private val radioManager: RadioManager,
    private val musicRepository: MusicRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    companion object {
        private const val TAG = "LyroQueueContinuation"
        const val EXTENSION_THRESHOLD = 4
        const val BATCH_SIZE = 15
        const val MAX_QUEUE_SIZE = 150
    }

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _canLoadMore = MutableStateFlow(true)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val mutex = Mutex()
    private var observationJob: Job? = null

    init {
        startPlaybackObservation()
    }

    /**
     * Observes playback position and queue length to trigger automatic safety prefetch
     * before the user reaches the end of the queue.
     */
    private fun startPlaybackObservation() {
        observationJob?.cancel()
        observationJob = coroutineScope.launch {
            combine(
                playbackManager.queue,
                playbackManager.currentIndex,
                radioManager.isRadioActive
            ) { queue, index, isRadio ->
                Triple(queue, index, isRadio)
            }.collect { (queue, index, isRadio) ->
                // Only act if Lyro Radio is NOT active and queue has tracks
                if (!isRadio && queue.isNotEmpty()) {
                    val remaining = queue.size - (index + 1)
                    if (remaining <= EXTENSION_THRESHOLD && queue.size < MAX_QUEUE_SIZE && !_isLoadingMore.value) {
                        Log.d(TAG, "Safety prefetch triggered: remaining=$remaining, queueSize=${queue.size}")
                        ensureMoreTracks()
                    }
                }
            }
        }
    }

    /**
     * Ensures more personalized tracks are appended to the queue.
     * Can be invoked from UI scroll near bottom or playback safety threshold.
     */
    fun ensureMoreTracks() {
        if (radioManager.isRadioActive.value) {
            Log.d(TAG, "ensureMoreTracks skipped: Lyro Radio is active and owns extension.")
            return
        }

        if (_isLoadingMore.value) {
            return
        }

        coroutineScope.launch {
            if (mutex.isLocked) return@launch
            mutex.withLock {
                if (radioManager.isRadioActive.value) return@withLock
                val currentQueue = playbackManager.queue.value
                if (currentQueue.isEmpty()) return@withLock

                if (currentQueue.size >= MAX_QUEUE_SIZE) {
                    Log.d(TAG, "Queue reached maximum size limit ($MAX_QUEUE_SIZE). Skipping extension.")
                    _canLoadMore.value = false
                    return@withLock
                }

                _isLoadingMore.value = true
                _loadError.value = null

                try {
                    val seedTrack = playbackManager.currentTrack.value ?: currentQueue.lastOrNull() ?: return@withLock
                    val isOnline = try { com.lyro.app.LyroApplication.instance.networkMonitor.isOnline.value } catch (e: Exception) { true }
                    val tasteProfile = tasteProfileRepository.tasteProfile.value
                    val existingIds = currentQueue.map { it.id }.toSet()
                    val batchIndex = (currentQueue.size / BATCH_SIZE).coerceAtLeast(0)

                    val candidates = if (isOnline) {
                        withContext(Dispatchers.IO) {
                            try {
                                candidateGenerator.generateRadioCandidatePool(
                                    seedTrack = seedTrack,
                                    tasteProfile = tasteProfile,
                                    extensionBatchIndex = batchIndex,
                                    targetPoolSize = 50
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "Online candidate pool generation failed: ${e.message}")
                                emptyList()
                            }
                        }
                    } else {
                        emptyList()
                    }

                    val rankedCandidates: List<PlayableTrack> = if (candidates.isNotEmpty()) {
                        withContext(Dispatchers.Default) {
                            ranker.rankCandidates(
                                candidates = candidates,
                                tasteProfile = tasteProfile,
                                recentlyShownIds = existingIds,
                                targetCount = BATCH_SIZE,
                                context = RecommendationContext.Home
                            ).map { it.track }
                        }
                    } else {
                        // Offline or network failure fallback: find local tracks
                        withContext(Dispatchers.IO) {
                            val allLocal = musicRepository.allSongs.value
                            allLocal.filter { !existingIds.contains(it.id.toString()) && !existingIds.contains("local_${it.id}") }
                                .shuffled()
                                .take(BATCH_SIZE)
                                .map { com.lyro.app.data.model.LocalTrack(song = it) }
                        }
                    }

                    val filteredCandidates = rankedCandidates.filter { track ->
                        !existingIds.contains(track.id) &&
                        (track.onlineVideoId == null || !existingIds.contains("online_${track.onlineVideoId}"))
                    }

                    if (filteredCandidates.isNotEmpty()) {
                        playbackManager.appendToQueue(filteredCandidates)
                        _loadError.value = null
                        Log.d(TAG, "Appended ${filteredCandidates.size} tracks to queue. Total: ${playbackManager.queue.value.size}")
                    } else {
                        if (!isOnline) {
                            _canLoadMore.value = false
                            _loadError.value = null
                            Log.d(TAG, "Offline continuation: No more local tracks available. Queue ends gracefully.")
                        } else {
                            _loadError.value = "Couldn't load more"
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in ensureMoreTracks: ${e.message}", e)
                    _loadError.value = "Couldn't load more"
                } finally {
                    _isLoadingMore.value = false
                }
            }
        }
    }

    /**
     * Retries loading more tracks after a failure.
     */
    fun retry() {
        _loadError.value = null
        ensureMoreTracks()
    }
}
