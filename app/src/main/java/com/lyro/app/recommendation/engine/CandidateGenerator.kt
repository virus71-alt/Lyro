package com.lyro.app.recommendation.engine

import android.util.Log
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.repository.OnlineMusicRepository
import com.lyro.app.recommendation.RecommendationConfig
import com.lyro.app.recommendation.data.ListeningEventRepository
import com.lyro.app.recommendation.model.CandidateTrack
import com.lyro.app.recommendation.model.RecommendationReason
import com.lyro.app.recommendation.model.TasteProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * Generates candidate tracks dynamically from OnlineMusicRepository using the user's
 * real TasteProfile affinities.
 *
 * Strictly avoids hardcoded artists or genres.
 */
class CandidateGenerator(
    private val onlineRepository: OnlineMusicRepository,
    private val eventRepository: ListeningEventRepository
) {
    companion object {
        private const val TAG = "LyroCandidateGen"

        // Neutral exploratory discovery seeds used exclusively for exploration ratio or cold-start fallback
        private val NEUTRAL_EXPLORATION_SEEDS = listOf(
            "Top Hits",
            "Trending Music",
            "Global Viral Hits",
            "New Music Friday",
            "Indie Acoustic Hits",
            "Chill Grooves",
            "Electronic Dance Music"
        )
    }

    private var explorationSeedIndex = 0

    /**
     * Dynamically generates a rich pool of 60-100 candidate tracks across:
     * - User's top affinity artists ("$artist songs")
     * - Adjacent discovery ("$artist similar songs")
     * - User's top language/genre/mood affinities (if present)
     * - Exploratory discovery seeds
     */
    suspend fun generateCandidatePool(
        tasteProfile: TasteProfile,
        targetPoolSize: Int = RecommendationConfig.CANDIDATE_POOL_SIZE
    ): List<CandidateTrack> = withContext(Dispatchers.IO) {
        val notInterested = eventRepository.getNotInterestedVideoIds()
        val candidateList = mutableListOf<CandidateTrack>()
        val seenVideoIds = mutableSetOf<String>()

        // Helper to query and collect tracks
        suspend fun searchAndCollect(query: String, reason: RecommendationReason) {
            try {
                val result = onlineRepository.searchSongs(query)
                result.getOrNull()?.forEach { track ->
                    if (!notInterested.contains(track.videoId) && seenVideoIds.add(track.videoId)) {
                        candidateList.add(CandidateTrack(track = track, reason = reason))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search query '$query' failed: ${e.message}")
            }
        }

        // 1. Top Affinity Artists Queries (multiple top artists, not just #1)
        val topArtists = tasteProfile.topArtists.take(RecommendationConfig.TOP_AFFINITY_ARTISTS_TO_QUERY)

        if (topArtists.isNotEmpty()) {
            val deferredQueries = mutableListOf<suspend () -> Unit>()

            // Top artist songs queries
            for ((artist, _) in topArtists.take(3)) {
                if (artist.isNotBlank()) {
                    deferredQueries.add {
                        searchAndCollect("$artist songs", RecommendationReason.TOP_ARTIST)
                    }
                }
            }

            // Adjacent discovery query for top artist
            val leadArtist = topArtists.firstOrNull()?.first
            if (!leadArtist.isNullOrBlank()) {
                deferredQueries.add {
                    searchAndCollect("$leadArtist similar songs", RecommendationReason.SIMILAR_ARTIST)
                }
            }

            // Secondary artist queries
            for ((artist, _) in topArtists.drop(3).take(2)) {
                if (artist.isNotBlank()) {
                    deferredQueries.add {
                        searchAndCollect("$artist music", RecommendationReason.TOP_ARTIST)
                    }
                }
            }

            // Top Language match query (if user has high language affinity)
            val topLanguage = tasteProfile.topLanguages.firstOrNull()?.first
            if (!topLanguage.isNullOrBlank()) {
                deferredQueries.add {
                    searchAndCollect("$topLanguage songs", RecommendationReason.LANGUAGE_MATCH)
                }
            }

            // Top Genre/Mood match query (if user has high genre/mood affinity)
            val topGenre = tasteProfile.topGenres.firstOrNull()?.first
            if (!topGenre.isNullOrBlank()) {
                deferredQueries.add {
                    searchAndCollect("$topGenre hits", RecommendationReason.GENRE_MATCH)
                }
            }

            // Top Mood query
            val topMood = tasteProfile.topMoods.firstOrNull()?.first
            if (!topMood.isNullOrBlank()) {
                deferredQueries.add {
                    searchAndCollect("$topMood music", RecommendationReason.MOOD_MATCH)
                }
            }

            // Exploratory query
            val exploreSeed = getNextExplorationSeed()
            deferredQueries.add {
                searchAndCollect(exploreSeed, RecommendationReason.EXPLORATION)
            }

            // Execute batch queries in parallel
            val jobs = deferredQueries.map { block ->
                async { block() }
            }
            jobs.awaitAll()
        } else {
            // Cold Start Fallback: No listening history or local library yet
            val seeds = listOf(
                getNextExplorationSeed(),
                getNextExplorationSeed(),
                getNextExplorationSeed()
            )
            val jobs = seeds.map { seed ->
                async {
                    searchAndCollect(seed, RecommendationReason.COLD_START_FALLBACK)
                }
            }
            jobs.awaitAll()
        }

        Log.d(
            TAG,
            "Generated candidate pool: count=${candidateList.size}, distinctVideoIds=${seenVideoIds.size}"
        )

        candidateList.take(targetPoolSize)
    }

    private fun getNextExplorationSeed(): String {
        val seed = NEUTRAL_EXPLORATION_SEEDS[explorationSeedIndex % NEUTRAL_EXPLORATION_SEEDS.size]
        explorationSeedIndex++
        return seed
    }
}
