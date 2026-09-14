package com.lyro.app.core.smartdownload

import android.util.Log
import com.lyro.app.core.matcher.LocalMediaIndex
import com.lyro.app.data.local.LyroDatabaseHelper
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.recommendation.engine.RecommendationEngine
import com.lyro.app.recommendation.model.RecommendationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScoredSmartCandidate(
    val track: OnlineTrack,
    val score: Float,
    val isDiscovery: Boolean
)

class SmartDownloadCandidateProvider(
    private val recommendationEngine: RecommendationEngine,
    private val dbHelper: LyroDatabaseHelper,
    private val localMediaIndex: LocalMediaIndex
) {
    companion object {
        private const val TAG = "LyroSmartCandidates"
        const val HIGH_CONFIDENCE_RATIO = 0.80f
        const val DISCOVERY_RATIO = 0.20f
    }

    suspend fun getCandidates(
        targetCount: Int = 40,
        activeTrackIds: Set<String> = emptySet()
    ): List<ScoredSmartCandidate> = withContext(Dispatchers.IO) {
        val profile = recommendationEngine.tasteProfile.value
        val recentEvents = recommendationEngine.eventRepository.getRecentEvents(100)

        // 1. Cold start / Taste confidence assessment
        val effectiveTarget = when {
            recentEvents.size < 15 -> minOf(targetCount, 15) // Low confidence
            recentEvents.size < 50 -> minOf(targetCount, 25) // Medium confidence
            else -> targetCount // High confidence
        }

        // 2. Build set of strict exclusions
        val downloadedVideoIds = dbHelper.getAllDownloadedMetadata().map { it.videoId }.toSet()
        val notInterested = dbHelper.getAllNotInterestedVideoIds()
        val localSongTitlesAndArtists = localMediaIndex.allLocalSongs.map {
            "${it.title.lowercase().trim()}:::${it.artist.lowercase().trim()}"
        }.toSet()

        // 3. Generate candidate pool from RecommendationEngine
        val rawPool = recommendationEngine.candidateGenerator.generateCandidatePool(profile)

        // 4. Apply strict filters:
        // - Not already downloaded (manual or smart)
        // - Not in not_interested
        // - Not in active 14-day cooldown
        // - Not already matching a local MediaStore audio file
        // - Not currently playing / in active queue
        val eligible = rawPool.filter { candidate ->
            val vid = candidate.videoId
            val key = "${candidate.title.lowercase().trim()}:::${candidate.artist.lowercase().trim()}"

            !downloadedVideoIds.contains(vid) &&
                    !notInterested.contains(vid) &&
                    !dbHelper.isSmartCooldownActive(vid) &&
                    !localSongTitlesAndArtists.contains(key) &&
                    !activeTrackIds.contains(vid)
        }

        if (eligible.isEmpty()) {
            Log.i(TAG, "No eligible smart download candidates found after filtering.")
            return@withContext emptyList()
        }

        val highConfidenceTarget = (effectiveTarget * HIGH_CONFIDENCE_RATIO).toInt().coerceAtLeast(1)
        val discoveryTarget = (effectiveTarget * DISCOVERY_RATIO).toInt().coerceAtLeast(1)

        // 5. Rank High-Confidence candidates (80%)
        val highConfidenceRanked = recommendationEngine.ranker.rankCandidates(
            candidates = eligible,
            tasteProfile = profile,
            targetCount = highConfidenceTarget,
            familiarRatio = 0.85f,
            adjacentRatio = 0.15f,
            explorationRatio = 0.0f,
            context = RecommendationContext.Home
        )

        val selectedVideoIds = highConfidenceRanked.map { it.videoId }.toSet()

        // 6. Rank Discovery candidates (20%) from remaining pool
        val discoveryPool = eligible.filter { !selectedVideoIds.contains(it.videoId) }
        val discoveryRanked = recommendationEngine.ranker.rankCandidates(
            candidates = discoveryPool,
            tasteProfile = profile,
            targetCount = discoveryTarget,
            familiarRatio = 0.15f,
            adjacentRatio = 0.45f,
            explorationRatio = 0.40f,
            context = RecommendationContext.Home
        )

        val result = mutableListOf<ScoredSmartCandidate>()
        for (cand in highConfidenceRanked) {
            val onlineTrack = cand.track as? OnlineTrack ?: OnlineTrack(
                videoId = cand.videoId,
                title = cand.title,
                artist = cand.artist,
                album = cand.album,
                durationMs = cand.track.durationMs,
                thumbnailUrl = cand.track.artworkUriString
            )
            result.add(ScoredSmartCandidate(onlineTrack, cand.score, isDiscovery = false))
        }

        for (cand in discoveryRanked) {
            val onlineTrack = cand.track as? OnlineTrack ?: OnlineTrack(
                videoId = cand.videoId,
                title = cand.title,
                artist = cand.artist,
                album = cand.album,
                durationMs = cand.track.durationMs,
                thumbnailUrl = cand.track.artworkUriString
            )
            result.add(ScoredSmartCandidate(onlineTrack, cand.score, isDiscovery = true))
        }

        Log.d(TAG, "Generated ${result.size} smart download candidates (${highConfidenceRanked.size} high-confidence, ${discoveryRanked.size} discovery)")
        result
    }
}
