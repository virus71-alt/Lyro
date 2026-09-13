package com.lyro.app.recommendation.radio

import android.util.Log
import com.lyro.app.core.matcher.TrackMetadataNormalizer
import com.lyro.app.data.model.LocalTrack
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.data.model.toLocalTrack
import com.lyro.app.data.repository.MusicRepository
import com.lyro.app.recommendation.RecommendationConfig
import com.lyro.app.recommendation.data.ListeningEventRepository
import com.lyro.app.recommendation.data.TasteProfileRepository
import com.lyro.app.recommendation.engine.CandidateGenerator
import com.lyro.app.recommendation.engine.RecommendationRanker
import com.lyro.app.recommendation.model.RecommendationContext
import com.lyro.app.service.PlaybackManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/**
 * Central manager coordinating Lyro Radio.
 * Manages active RadioSession lifecycle, candidate fetching, ranking, endless queue auto-extension,
 * live adaptation, and background reliability.
 */
class RadioManager(
    private val playbackManager: PlaybackManager,
    private val candidateGenerator: CandidateGenerator,
    private val ranker: RecommendationRanker,
    private val tasteProfileRepository: TasteProfileRepository,
    private val eventRepository: ListeningEventRepository,
    private val musicRepository: MusicRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    companion object {
        private const val TAG = "LyroRadioManager"
    }

    private val _isRadioActive = MutableStateFlow(false)
    val isRadioActive: StateFlow<Boolean> = _isRadioActive.asStateFlow()

    private val _currentSession = MutableStateFlow<RadioSession?>(null)
    val currentSession: StateFlow<RadioSession?> = _currentSession.asStateFlow()

    private val extensionMutex = Mutex()
    private var queueObservationJob: Job? = null

    init {
        startQueueObservation()
    }

    /**
     * Starts Lyro Radio seeded from the given track.
     * Generates initial ~20 tracks, sets up queue, and starts/continues playback.
     */
    fun startSongRadio(seedTrack: PlayableTrack) {
        coroutineScope.launch {
            Log.d(TAG, "Starting Song Radio for seed: '${seedTrack.title}' by '${seedTrack.artist}'")
            val session = RadioSession(
                radioType = RadioType.SONG,
                seedTrack = seedTrack
            )
            _currentSession.value = session
            _isRadioActive.value = true

            val profile = tasteProfileRepository.tasteProfile.value
            val initialCandidates = try {
                candidateGenerator.generateRadioCandidatePool(
                    seedTrack = seedTrack,
                    tasteProfile = profile,
                    extensionBatchIndex = 0,
                    targetPoolSize = 60
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error generating initial radio candidates: ${e.message}", e)
                emptyList()
            }

            val rankedCandidates = if (initialCandidates.isNotEmpty()) {
                ranker.rankCandidates(
                    candidates = initialCandidates,
                    tasteProfile = profile,
                    recentlyShownIds = session.generatedTrackIds,
                    targetCount = RecommendationConfig.RADIO_INITIAL_QUEUE_SIZE,
                    context = RecommendationContext.Radio(
                        seedTrack = seedTrack,
                        recentSessionTaste = profile.recentSessionArtists,
                        extensionBatchIndex = 0,
                        sessionTrackIds = session.generatedTrackIds,
                        sessionSkippedIds = session.recentlySkippedIds,
                        sessionSkippedArtists = session.recentlySkippedArtists
                    )
                ).map { it.track }
            } else {
                // Offline / network failure fallback: find local tracks from same or similar artists
                findOfflineFallbackCandidates(seedTrack, count = RecommendationConfig.RADIO_INITIAL_QUEUE_SIZE)
            }

            session.recordGeneratedTracks(rankedCandidates)
            val fullQueue = listOf(seedTrack) + rankedCandidates

            // If seed is already currently playing, update upcoming queue without restarting playback
            val curTrack = playbackManager.currentTrack.value
            val isAlreadyPlayingSeed = curTrack?.id == seedTrack.id && playbackManager.isPlaying.value

            if (isAlreadyPlayingSeed) {
                Log.d(TAG, "Seed is already playing. Updating upcoming queue smoothly.")
                playbackManager.playTrack(seedTrack, fullQueue, startIndex = 0, isRadio = true)
            } else {
                playbackManager.playTrack(seedTrack, fullQueue, startIndex = 0, isRadio = true)
            }
        }
    }

    /**
     * Observes remaining tracks in the queue and triggers auto-extension when remaining <= threshold.
     */
    private fun startQueueObservation() {
        queueObservationJob?.cancel()
        queueObservationJob = coroutineScope.launch {
            playbackManager.currentIndex.collect { curIdx ->
                if (!_isRadioActive.value) return@collect
                val session = _currentSession.value ?: return@collect
                val queue = playbackManager.queue.value
                val remaining = queue.size - 1 - curIdx

                if (remaining <= RecommendationConfig.RADIO_EXTENSION_THRESHOLD && remaining >= 0) {
                    extendRadioSession(session)
                }
            }
        }
    }

    /**
     * Auto-extends the active radio session by appending another batch of personalized tracks.
     */
    fun extendRadioSession(session: RadioSession) {
        if (!_isRadioActive.value || !session.isActive) return

        coroutineScope.launch(Dispatchers.IO) {
            if (!extensionMutex.tryLock()) {
                // Already extending
                return@launch
            }
            try {
                Log.d(TAG, "Auto-extending radio session: batch=${session.extensionBatchCount}")
                val profile = tasteProfileRepository.tasteProfile.value
                val candidates = candidateGenerator.generateRadioCandidatePool(
                    seedTrack = session.seedTrack,
                    tasteProfile = profile,
                    extensionBatchIndex = session.extensionBatchCount,
                    targetPoolSize = 45
                )

                val ranked = if (candidates.isNotEmpty()) {
                    ranker.rankCandidates(
                        candidates = candidates,
                        tasteProfile = profile,
                        recentlyShownIds = session.generatedTrackIds,
                        targetCount = RecommendationConfig.RADIO_EXTENSION_BATCH_SIZE,
                        context = RecommendationContext.Radio(
                            seedTrack = session.seedTrack,
                            recentSessionTaste = profile.recentSessionArtists,
                            extensionBatchIndex = session.extensionBatchCount,
                            sessionTrackIds = session.generatedTrackIds,
                            sessionSkippedIds = session.recentlySkippedIds,
                            sessionSkippedArtists = session.recentlySkippedArtists
                        )
                    ).map { it.track }
                } else {
                    findOfflineFallbackCandidates(session.seedTrack, count = RecommendationConfig.RADIO_EXTENSION_BATCH_SIZE)
                }

                if (ranked.isNotEmpty()) {
                    session.recordGeneratedTracks(ranked)
                    withContext(Dispatchers.Main) {
                        playbackManager.appendToQueue(ranked)
                    }
                    Log.d(TAG, "Radio session auto-extended with ${ranked.size} tracks")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to auto-extend radio session: ${e.message}", e)
            } finally {
                extensionMutex.unlock()
            }
        }
    }

    /**
     * Offline fallback candidate finder using local MediaStore tracks and downloads.
     */
    private fun findOfflineFallbackCandidates(seedTrack: PlayableTrack, count: Int): List<PlayableTrack> {
        val normSeedArtist = TrackMetadataNormalizer.normalizeArtist(seedTrack.artist)
        val allLocal = musicRepository.allSongs.value
        val sameArtist = allLocal.filter {
            TrackMetadataNormalizer.normalizeArtist(it.artist) == normSeedArtist && it.id.toString() != seedTrack.id
        }.map { it.toLocalTrack() }

        val otherLocal = allLocal.filter {
            TrackMetadataNormalizer.normalizeArtist(it.artist) != normSeedArtist
        }.shuffled().take(count - sameArtist.size).map { it.toLocalTrack() }

        return (sameArtist.take(RecommendationConfig.RADIO_MAX_TRACKS_PER_ARTIST) + otherLocal).take(count)
    }

    /**
     * Records a skip on a track during the active radio session.
     */
    fun onTrackSkipped(track: PlayableTrack) {
        val session = _currentSession.value
        if (_isRadioActive.value && session != null) {
            session.recordSkip(track)
            Log.d(TAG, "Radio recorded skip for live adaptation: '${track.title}'")
        }
    }

    /**
     * Called when the user initiates manual playback from outside Radio.
     * Cleanly ends the radio session.
     */
    fun onManualTrackPlay(track: PlayableTrack) {
        if (_isRadioActive.value) {
            Log.d(TAG, "Manual playback initiated for '${track.title}'. Stopping Radio.")
            stopRadio()
        }
    }

    /**
     * Stops the active Radio session without stopping playback or clearing the queue.
     * The remaining queue becomes a normal static queue.
     */
    fun stopRadio() {
        if (_isRadioActive.value) {
            _isRadioActive.value = false
            _currentSession.value?.isActive = false
            _currentSession.value = null
            Log.d(TAG, "Lyro Radio stopped. Remaining queue preserved as static queue.")
        }
    }
}
