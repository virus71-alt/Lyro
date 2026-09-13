package com.lyro.app.recommendation.engine

import android.util.Log
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.data.model.Song
import com.lyro.app.recommendation.RecommendationConfig
import com.lyro.app.recommendation.data.ListeningEventRepository
import com.lyro.app.recommendation.data.TasteProfileRepository
import com.lyro.app.recommendation.model.ListeningEvent
import com.lyro.app.recommendation.model.PlaybackSession
import com.lyro.app.recommendation.model.RecommendationReason
import com.lyro.app.recommendation.model.TasteProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * High-level orchestration engine for Lyro's on-device personalized recommendation brain.
 * Coordinates profile evolution, dynamic candidate querying, ranking, and session tracking.
 */
class RecommendationEngine(
    val eventRepository: ListeningEventRepository,
    val tasteProfileRepository: TasteProfileRepository,
    private val candidateGenerator: CandidateGenerator,
    private val ranker: RecommendationRanker
) {
    companion object {
        private const val TAG = "LyroRecommendationEngine"
    }

    val tasteProfile: StateFlow<TasteProfile> = tasteProfileRepository.tasteProfile

    // Active playback session currently playing
    @Volatile
    var activeSession: PlaybackSession? = null
        private set

    /**
     * Recomputes the user's taste profile using available cold-start signals.
     */
    suspend fun recomputeTasteProfile(
        localSongs: List<Song> = emptyList(),
        downloadedTracks: List<com.lyro.app.data.model.OnlineTrack> = emptyList()
    ): TasteProfile {
        return tasteProfileRepository.recomputeProfile(localSongs, downloadedTracks)
    }

    /**
     * Starts a new playback session when a track begins playing.
     */
    fun startPlaybackSession(
        track: PlayableTrack,
        isManual: Boolean = true,
        playSource: String = "home"
    ): PlaybackSession {
        val session = PlaybackSession(
            track = track,
            isManual = isManual,
            playSource = playSource
        )
        activeSession = session

        // Record play start event
        val startEvent = ListeningEvent(
            playbackSessionId = session.sessionId,
            videoId = session.videoId,
            title = session.title,
            artist = session.artist,
            album = session.album,
            eventType = if (isManual) com.lyro.app.recommendation.model.EventType.MANUAL_PLAY
            else com.lyro.app.recommendation.model.EventType.AUTO_PLAY,
            timestamp = System.currentTimeMillis(),
            durationMs = track.durationMs,
            isManual = isManual
        )
        eventRepository.recordEvent(startEvent)
        return session
    }

    /**
     * Checks milestones during ongoing playback (called periodically from playback position updates).
     */
    fun onPlaybackProgress(session: PlaybackSession, positionMs: Long, totalDurationMs: Long) {
        val newMilestones = session.checkMilestones(positionMs, totalDurationMs)
        for (event in newMilestones) {
            eventRepository.recordEvent(event)
        }
    }

    /**
     * Handles manual skip initiated by user.
     */
    fun onManualSkip(session: PlaybackSession) {
        session.onManualSkip()?.let { skipEvent ->
            eventRepository.recordEvent(skipEvent)
        }
        if (activeSession?.sessionId == session.sessionId) {
            activeSession = null
        }
    }

    /**
     * Handles natural completion of a track.
     */
    fun onTrackCompleted(session: PlaybackSession) {
        session.onCompleted()?.let { completeEvent ->
            eventRepository.recordEvent(completeEvent)
        }
        if (activeSession?.sessionId == session.sessionId) {
            activeSession = null
        }
    }

    /**
     * Marks a track as "Not Interested".
     */
    fun markNotInterested(track: PlayableTrack) {
        val videoId = track.onlineVideoId ?: track.id
        eventRepository.markNotInterested(videoId, track.title, track.artist)
    }

    /**
     * Generates personalized tracks for "Quick Picks".
     * Favors high taste affinity and recent session taste.
     */
    suspend fun getPersonalizedQuickPicks(
        targetCount: Int = 16
    ): List<PlayableTrack> = withContext(Dispatchers.IO) {
        val profile = tasteProfile.value
        val recentlyShown = eventRepository.getRecentlyRecommendedVideoIds()
        val candidatePool = candidateGenerator.generateCandidatePool(profile)

        val ranked = ranker.rankCandidates(
            candidates = candidatePool,
            tasteProfile = profile,
            recentlyShownIds = recentlyShown,
            targetCount = targetCount,
            familiarRatio = 0.85f,
            adjacentRatio = 0.10f,
            explorationRatio = 0.05f
        )

        val tracks = ranked.map { it.track }
        eventRepository.recordRecentlyRecommended(tracks.mapNotNull { it.onlineVideoId }, "quick_picks")
        tracks
    }

    /**
     * Generates personalized tracks for "Recommended for you".
     * Balanced long-term taste + adjacent discovery.
     */
    suspend fun getPersonalizedRecommendations(
        targetCount: Int = 16
    ): List<PlayableTrack> = withContext(Dispatchers.IO) {
        val profile = tasteProfile.value
        val recentlyShown = eventRepository.getRecentlyRecommendedVideoIds()
        val candidatePool = candidateGenerator.generateCandidatePool(profile)

        val ranked = ranker.rankCandidates(
            candidates = candidatePool,
            tasteProfile = profile,
            recentlyShownIds = recentlyShown,
            targetCount = targetCount,
            familiarRatio = RecommendationConfig.FAMILIAR_RATIO,
            adjacentRatio = RecommendationConfig.ADJACENT_RATIO,
            explorationRatio = RecommendationConfig.EXPLORATION_RATIO
        )

        val tracks = ranked.map { it.track }
        eventRepository.recordRecentlyRecommended(tracks.mapNotNull { it.onlineVideoId }, "recommended")
        tracks
    }

    /**
     * Generates personalized tracks for "Discover something new".
     * Emphasizes exploration and adjacent discoveries.
     */
    suspend fun getPersonalizedDiscover(
        targetCount: Int = 16
    ): List<PlayableTrack> = withContext(Dispatchers.IO) {
        val profile = tasteProfile.value
        val recentlyShown = eventRepository.getRecentlyRecommendedVideoIds()
        val candidatePool = candidateGenerator.generateCandidatePool(profile)

        val ranked = ranker.rankCandidates(
            candidates = candidatePool,
            tasteProfile = profile,
            recentlyShownIds = recentlyShown,
            targetCount = targetCount,
            familiarRatio = 0.20f,
            adjacentRatio = 0.40f,
            explorationRatio = 0.40f
        )

        val tracks = ranked.map { it.track }
        eventRepository.recordRecentlyRecommended(tracks.mapNotNull { it.onlineVideoId }, "discover")
        tracks
    }

    /**
     * Suggests the next track for Radio / Autoplay based on the current playing track
     * and queue context.
     */
    suspend fun recommendNext(
        currentTrack: PlayableTrack?,
        queueContext: List<PlayableTrack>
    ): PlayableTrack? = withContext(Dispatchers.IO) {
        val baseProfile = tasteProfile.value
        val contextProfile = if (currentTrack != null) {
            val normCurrentArtist = com.lyro.app.core.matcher.TrackMetadataNormalizer.normalizeArtist(currentTrack.artist)
            val updatedArtists = baseProfile.artistAffinities.toMutableMap()
            updatedArtists[normCurrentArtist] = (updatedArtists[normCurrentArtist] ?: 0.5f).coerceAtLeast(0.85f)
            baseProfile.copy(
                artistAffinities = updatedArtists,
                recentSessionArtists = baseProfile.recentSessionArtists + normCurrentArtist
            )
        } else {
            baseProfile
        }
        val queueIds = queueContext.map { it.onlineVideoId ?: it.id }.toSet()

        // Generate candidates based on context and recent taste
        val candidates = candidateGenerator.generateCandidatePool(contextProfile)
        val filtered = candidates.filter { !queueIds.contains(it.videoId) }

        val ranked = ranker.rankCandidates(
            candidates = filtered,
            tasteProfile = contextProfile,
            recentlyShownIds = queueIds,
            targetCount = 1,
            familiarRatio = 0.70f,
            adjacentRatio = 0.30f,
            explorationRatio = 0.00f
        )

        ranked.firstOrNull()?.track
    }
}
