package com.lyro.app.recommendation.engine

import android.util.Log
import com.lyro.app.core.matcher.TrackMetadataNormalizer
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.recommendation.RecommendationConfig
import com.lyro.app.recommendation.model.CandidateTrack
import com.lyro.app.recommendation.model.RecommendationContext
import com.lyro.app.recommendation.model.RecommendationReason
import com.lyro.app.recommendation.model.TasteProfile
import kotlin.math.max

/**
 * Personalized ranker that scores candidates, balances familiarity vs discovery ratios,
 * and enforces artist & album diversity constraints.
 * Supports both Home recommendations and Lyro Radio.
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
        explorationRatio: Float = RecommendationConfig.EXPLORATION_RATIO,
        context: RecommendationContext = RecommendationContext.Home
    ): List<CandidateTrack> {
        if (candidates.isEmpty()) return emptyList()

        val isRadio = context is RecommendationContext.Radio
        val maxPerArtist = if (isRadio) RecommendationConfig.RADIO_MAX_TRACKS_PER_ARTIST else RecommendationConfig.MAX_TRACKS_PER_ARTIST
        val maxPerAlbum = if (isRadio) RecommendationConfig.RADIO_MAX_TRACKS_PER_ALBUM else RecommendationConfig.MAX_TRACKS_PER_ALBUM

        // 1. Score each candidate
        val scoredCandidates = candidates.mapNotNull { candidate ->
            val score = calculateScore(candidate, tasteProfile, recentlyShownIds, overplayedIds, context)
            if (score < -500f) {
                // Filter out hard exclusions (e.g. duplicate track in current radio session)
                null
            } else {
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
            }
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
            if (currentArtistCount >= maxPerArtist) {
                return false
            }

            candidate.album?.let { albumName ->
                val normAlbum = albumName.trim().lowercase()
                val currentAlbumCount = albumCountMap[normAlbum] ?: 0
                if (currentAlbumCount >= maxPerAlbum) {
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

        // 5. If Radio, ensure no immediate consecutive repetition of the same artist
        if (isRadio && selected.size > 2) {
            for (i in 0 until selected.size - 1) {
                val currentArtist = TrackMetadataNormalizer.normalizeArtist(selected[i].artist)
                val nextArtist = TrackMetadataNormalizer.normalizeArtist(selected[i + 1].artist)
                if (currentArtist == nextArtist) {
                    // Find the next track with a different artist to swap
                    val swapIndex = (i + 2 until selected.size).firstOrNull { idx ->
                        TrackMetadataNormalizer.normalizeArtist(selected[idx].artist) != currentArtist
                    }
                    if (swapIndex != null) {
                        val temp = selected[i + 1]
                        selected[i + 1] = selected[swapIndex]
                        selected[swapIndex] = temp
                    }
                }
            }
        }

        Log.d(
            TAG,
            "Ranked recommendations: selected=${selected.size}, familiar=$familiarAdded, adjacent=$adjacentAdded, exploration=$explorationAdded, isRadio=$isRadio"
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
     * - Specialized Radio weighting & seed decay
     */
    fun calculateScore(
        candidate: CandidateTrack,
        tasteProfile: TasteProfile,
        recentlyShownIds: Set<String>,
        overplayedIds: Set<String>,
        context: RecommendationContext = RecommendationContext.Home
    ): Float {
        return when (context) {
            is RecommendationContext.Home -> {
                val artistAffinity = tasteProfile.getArtistAffinity(candidate.artist)
                val genreAffinity = 0.0f
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

                baseScore - skipPenalty - shownPenalty - overplayedPenalty
            }
            is RecommendationContext.Radio -> {
                val normCandidateArtist = TrackMetadataNormalizer.normalizeArtist(candidate.artist)
                val normSeedArtist = TrackMetadataNormalizer.normalizeArtist(context.seedTrack.artist)

                // Rule: no duplicate track in session
                val isSeedDuplicate = candidate.videoId == context.seedTrack.onlineVideoId ||
                        candidate.track.id == context.seedTrack.id ||
                        context.sessionTrackIds.contains(candidate.videoId) ||
                        context.sessionTrackIds.contains(candidate.track.id)

                if (isSeedDuplicate) {
                    return -1000f
                }

                // Seed similarity
                val isSeedArtist = normCandidateArtist == normSeedArtist
                val seedSimilarity = if (isSeedArtist) 1.0f else if (candidate.reason == RecommendationReason.SIMILAR_ARTIST) 0.75f else 0.0f

                // Seed influence decay across batches
                val decayMultiplier = Math.pow(RecommendationConfig.RADIO_SEED_DECAY_RATE.toDouble(), context.extensionBatchIndex.toDouble()).toFloat()
                val effectiveSeedWeight = RecommendationConfig.RADIO_SEED_SIMILARITY_WEIGHT * decayMultiplier
                val freedWeight = RecommendationConfig.RADIO_SEED_SIMILARITY_WEIGHT - effectiveSeedWeight
                val effectiveLongTermWeight = RecommendationConfig.RADIO_LONG_TERM_TASTE_WEIGHT + (freedWeight * 0.6f)
                val effectiveExplorationWeight = RecommendationConfig.RADIO_EXPLORATION_WEIGHT + (freedWeight * 0.4f)

                // User Taste signals
                val artistAffinity = tasteProfile.getArtistAffinity(candidate.artist)
                val isRecentSession = tasteProfile.isArtistInRecentSession(candidate.artist) || context.recentSessionTaste.contains(normCandidateArtist)
                val sessionBonus = if (isRecentSession) 1.0f else 0.0f

                val explorationBonus = if (candidate.reason == RecommendationReason.EXPLORATION || candidate.reason == RecommendationReason.COLD_START_FALLBACK) 1.0f else 0.0f

                val baseScore = (seedSimilarity * effectiveSeedWeight) +
                        (sessionBonus * RecommendationConfig.RADIO_RECENT_SESSION_WEIGHT) +
                        (artistAffinity * effectiveLongTermWeight) +
                        (artistAffinity * RecommendationConfig.RADIO_ARTIST_AFFINITY_WEIGHT) +
                        (explorationBonus * effectiveExplorationWeight)

                // Penalties
                val isSessionSkipped = context.sessionSkippedIds.contains(candidate.videoId) ||
                        context.sessionSkippedIds.contains(candidate.track.id)
                val isSessionSkippedArtist = context.sessionSkippedArtists.contains(normCandidateArtist)
                val isGeneralSkippedArtist = tasteProfile.isArtistRecentlySkipped(candidate.artist)

                val skipPenalty = (if (isSessionSkipped) 0.60f else 0.0f) +
                        (if (isSessionSkippedArtist) 0.40f else 0.0f) +
                        (if (isGeneralSkippedArtist) 0.25f else 0.0f)

                val shownPenalty = if (recentlyShownIds.contains(candidate.videoId)) RecommendationConfig.PENALTY_RECENTLY_SHOWN else 0.0f
                val overplayedPenalty = if (overplayedIds.contains(candidate.videoId)) RecommendationConfig.PENALTY_OVERPLAYED else 0.0f

                baseScore - skipPenalty - shownPenalty - overplayedPenalty
            }
        }
    }
}
