package com.lyro.app.recommendation.engine

import android.util.Log
import com.lyro.app.core.matcher.TrackMetadataNormalizer
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.recommendation.RecommendationConfig
import com.lyro.app.recommendation.model.CandidateTrack
import com.lyro.app.recommendation.model.RecommendationReason
import com.lyro.app.recommendation.model.TasteProfile
import kotlin.math.max

/**
 * Personalized ranker that scores candidates, balances familiarity vs discovery ratios,
 * and enforces artist & album diversity constraints.
 */
class RecommendationRanker {

    companion object {
        private const val TAG = "LyroRecommendationRanker"
    }

    /**
     * Scores and ranks candidate tracks, enforcing diversity rules and familiarity ratios.
     */
    fun rankCandidates(
        candidates: List<CandidateTrack>,
        tasteProfile: TasteProfile,
        recentlyShownIds: Set<String> = emptySet(),
        overplayedIds: Set<String> = emptySet(),
        targetCount: Int = 16,
        familiarRatio: Float = RecommendationConfig.FAMILIAR_RATIO,
        adjacentRatio: Float = RecommendationConfig.ADJACENT_RATIO,
        explorationRatio: Float = RecommendationConfig.EXPLORATION_RATIO
    ): List<CandidateTrack> {
        if (candidates.isEmpty()) return emptyList()

        // 1. Score each candidate
        val scoredCandidates = candidates.map { candidate ->
            val score = calculateScore(candidate, tasteProfile, recentlyShownIds, overplayedIds)
            val isFamiliar = candidate.reason == RecommendationReason.TOP_ARTIST ||
                    tasteProfile.getArtistAffinity(candidate.artist) > 0.40f ||
                    candidate.track.isFavorite
            val isAdjacent = candidate.reason == RecommendationReason.SIMILAR_ARTIST ||
                    candidate.reason == RecommendationReason.LANGUAGE_MATCH ||
                    candidate.reason == RecommendationReason.GENRE_MATCH
            val isExploration = candidate.reason == RecommendationReason.EXPLORATION ||
                    candidate.reason == RecommendationReason.COLD_START_FALLBACK

            candidate.copy(
                score = score,
                isFamiliar = isFamiliar,
                isAdjacent = isAdjacent,
                isExploration = isExploration
            )
        }.sortedByDescending { it.score }

        // 2. Partition into pools
        val familiarPool = scoredCandidates.filter { it.isFamiliar }
        val adjacentPool = scoredCandidates.filter { it.isAdjacent && !it.isFamiliar }
        val explorationPool = scoredCandidates.filter { it.isExploration || (!it.isFamiliar && !it.isAdjacent) }

        // 3. Compute target counts per bucket
        val targetFamiliar = (targetCount * familiarRatio).toInt().coerceAtLeast(1)
        val targetAdjacent = (targetCount * adjacentRatio).toInt().coerceAtLeast(1)
        val targetExploration = max(1, (targetCount * explorationRatio).toInt().coerceAtLeast(targetCount - targetFamiliar - targetAdjacent))

        // 4. Select with diversity constraints
        val selected = mutableListOf<CandidateTrack>()
        val artistCountMap = mutableMapOf<String, Int>()
        val albumCountMap = mutableMapOf<String, Int>()
        val selectedVideoIds = mutableSetOf<String>()

        fun tryAddTrack(candidate: CandidateTrack): Boolean {
            if (selectedVideoIds.contains(candidate.videoId)) return false

            val normArtist = TrackMetadataNormalizer.normalizeArtist(candidate.artist)
            val currentArtistCount = artistCountMap[normArtist] ?: 0
            if (currentArtistCount >= RecommendationConfig.MAX_TRACKS_PER_ARTIST) {
                return false
            }

            candidate.album?.let { albumName ->
                val normAlbum = albumName.trim().lowercase()
                val currentAlbumCount = albumCountMap[normAlbum] ?: 0
                if (currentAlbumCount >= RecommendationConfig.MAX_TRACKS_PER_ALBUM) {
                    return false
                }
            }

            selected.add(candidate)
            selectedVideoIds.add(candidate.videoId)
            artistCountMap[normArtist] = currentArtistCount + 1
            candidate.album?.let { albumName ->
                val normAlbum = albumName.trim().lowercase()
                albumCountMap[normAlbum] = (albumCountMap[normAlbum] ?: 0) + 1
            }
            return true
        }

        // Fill familiar portion
        var familiarAdded = 0
        for (cand in familiarPool) {
            if (familiarAdded >= targetFamiliar) break
            if (tryAddTrack(cand)) familiarAdded++
        }

        // Fill adjacent portion
        var adjacentAdded = 0
        for (cand in adjacentPool) {
            if (adjacentAdded >= targetAdjacent) break
            if (tryAddTrack(cand)) adjacentAdded++
        }

        // Fill exploration portion
        var explorationAdded = 0
        for (cand in explorationPool) {
            if (explorationAdded >= targetExploration) break
            if (tryAddTrack(cand)) explorationAdded++
        }

        // If targetCount not reached due to bucket starvation, fill from remaining scored candidates
        if (selected.size < targetCount) {
            for (cand in scoredCandidates) {
                if (selected.size >= targetCount) break
                tryAddTrack(cand)
            }
        }

        Log.d(
            TAG,
            "Ranked recommendations: selected=${selected.size}, familiar=$familiarAdded, adjacent=$adjacentAdded, exploration=$explorationAdded"
        )

        return selected
    }

    /**
     * Multi-factor scoring formula incorporating:
     * - Artist affinity
     * - Genre / Mood / Language affinity
     * - Recent session similarity
     * - Novelty bonus
     * - Penalties: recent skip, overplayed, recently shown
     */
    fun calculateScore(
        candidate: CandidateTrack,
        tasteProfile: TasteProfile,
        recentlyShownIds: Set<String>,
        overplayedIds: Set<String>
    ): Float {
        val artistAffinity = tasteProfile.getArtistAffinity(candidate.artist)
        val genreAffinity = 0.0f // OnlineTrack may not expose genre directly, defaults safely to 0
        val languageAffinity = 0.0f
        val moodAffinity = 0.0f

        val sessionBonus = if (tasteProfile.isArtistInRecentSession(candidate.artist)) 1.0f else 0.0f
        val noveltyBonus = if (!candidate.track.isDownloaded && !recentlyShownIds.contains(candidate.videoId)) {
            RecommendationConfig.RANK_NOVELTY_BONUS
        } else {
            0.0f
        }

        // Penalties
        val skipPenalty = if (tasteProfile.isArtistRecentlySkipped(candidate.artist)) {
            RecommendationConfig.PENALTY_RECENTLY_SKIPPED
        } else {
            0.0f
        }

        val shownPenalty = if (recentlyShownIds.contains(candidate.videoId)) {
            RecommendationConfig.PENALTY_RECENTLY_SHOWN
        } else {
            0.0f
        }

        val overplayedPenalty = if (overplayedIds.contains(candidate.videoId) && !candidate.track.isFavorite) {
            RecommendationConfig.PENALTY_OVERPLAYED
        } else {
            0.0f
        }

        val baseScore = (artistAffinity * RecommendationConfig.RANK_ARTIST_WEIGHT) +
                (genreAffinity * RecommendationConfig.RANK_GENRE_WEIGHT) +
                (languageAffinity * RecommendationConfig.RANK_LANGUAGE_WEIGHT) +
                (moodAffinity * RecommendationConfig.RANK_MOOD_WEIGHT) +
                (sessionBonus * RecommendationConfig.RANK_SESSION_WEIGHT) +
                noveltyBonus

        val finalScore = baseScore - skipPenalty - shownPenalty - overplayedPenalty
        return finalScore
    }
}
