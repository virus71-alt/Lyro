package com.lyro.app.recommendation.data

import android.util.Log
import com.lyro.app.core.matcher.TrackMetadataNormalizer
import com.lyro.app.data.local.LyroDatabaseHelper
import com.lyro.app.data.model.Song
import com.lyro.app.recommendation.RecommendationConfig
import com.lyro.app.recommendation.model.EventType
import com.lyro.app.recommendation.model.ListeningEvent
import com.lyro.app.recommendation.model.TasteProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Repository that aggregates listening events with exponential time decay,
 * integrates cold-start bootstrapping (local MediaStore, downloads, favorites),
 * and computes the user's multi-dimensional TasteProfile.
 */
class TasteProfileRepository(
    private val dbHelper: LyroDatabaseHelper? = null,
    private val eventRepository: ListeningEventRepository
) {
    companion object {
        private const val TAG = "LyroTasteProfile"
    }

    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _tasteProfile = MutableStateFlow(TasteProfile())
    val tasteProfile: StateFlow<TasteProfile> = _tasteProfile.asStateFlow()

    init {
        // 1. Instantly load snapshot from database for zero-latency cold launch
        loadCachedSnapshot()

        // 2. Observe live events to trigger debounced re-computation
        repositoryScope.launch {
            eventRepository.eventStream.collect {
                // Recompute profile when a significant event arrives
                recomputeProfileInternal()
            }
        }
    }

    private fun loadCachedSnapshot() {
        if (dbHelper == null) return
        try {
            val artists = dbHelper.loadTasteSnapshots("artist")
            val genres = dbHelper.loadTasteSnapshots("genre")
            val moods = dbHelper.loadTasteSnapshots("mood")
            val languages = dbHelper.loadTasteSnapshots("language")

            if (artists.isNotEmpty() || genres.isNotEmpty() || languages.isNotEmpty() || moods.isNotEmpty()) {
                _tasteProfile.value = TasteProfile(
                    artistAffinities = artists,
                    genreAffinities = genres,
                    moodAffinities = moods,
                    languageAffinities = languages
                )
                Log.d(TAG, "Loaded cached taste snapshot: topArtists=${artists.entries.take(3)}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load cached taste snapshot: ${e.message}")
        }
    }

    /**
     * Triggers full re-computation of the taste profile.
     */
    suspend fun recomputeProfile(
        localSongs: List<Song> = emptyList(),
        downloadedTracks: List<com.lyro.app.data.model.OnlineTrack> = emptyList()
    ): TasteProfile = withContext(Dispatchers.Default) {
        val profile = computeProfile(localSongs, downloadedTracks)
        _tasteProfile.value = profile

        // Persist snapshots to DB if available
        if (dbHelper != null) {
            withContext(Dispatchers.IO) {
                dbHelper.saveTasteSnapshots("artist", profile.artistAffinities)
                dbHelper.saveTasteSnapshots("genre", profile.genreAffinities)
                dbHelper.saveTasteSnapshots("mood", profile.moodAffinities)
                dbHelper.saveTasteSnapshots("language", profile.languageAffinities)
            }
        }

        profile
    }

    private suspend fun recomputeProfileInternal() {
        try {
            val profile = computeProfile()
            _tasteProfile.value = profile
            if (dbHelper != null) {
                withContext(Dispatchers.IO) {
                    dbHelper.saveTasteSnapshots("artist", profile.artistAffinities)
                    dbHelper.saveTasteSnapshots("genre", profile.genreAffinities)
                    dbHelper.saveTasteSnapshots("mood", profile.moodAffinities)
                    dbHelper.saveTasteSnapshots("language", profile.languageAffinities)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in recomputeProfileInternal: ${e.message}", e)
        }
    }

    /**
     * Computes the TasteProfile by applying exponential decay to past events,
     * aggregating recent session taste, and bootstrapping cold start signals.
     */
    suspend fun computeProfile(
        localSongs: List<Song> = emptyList(),
        downloadedTracks: List<com.lyro.app.data.model.OnlineTrack> = emptyList(),
        referenceTime: Long = System.currentTimeMillis()
    ): TasteProfile = withContext(Dispatchers.Default) {
        val events = eventRepository.getRecentEvents(RecommendationConfig.MAX_PERSISTED_EVENTS)

        val rawArtistPoints = mutableMapOf<String, Float>()
        val rawGenrePoints = mutableMapOf<String, Float>()
        val rawMoodPoints = mutableMapOf<String, Float>()
        val rawLanguagePoints = mutableMapOf<String, Float>()

        val recentSessionArtists = mutableSetOf<String>()
        val recentSkippedArtists = mutableSetOf<String>()

        // Calculate session threshold
        val recentCutoff = referenceTime - RecommendationConfig.RECENT_SESSION_WINDOW_MS
        val distinctSessions = events.map { it.playbackSessionId }.distinct().take(RecommendationConfig.MAX_RECENT_SESSIONS_COUNT).toSet()

        // 1. Process Historical Listening Events with Exponential Time Decay
        for (event in events) {
            val elapsedMs = max(0L, referenceTime - event.timestamp)
            val decay = exp(-RecommendationConfig.DECAY_LAMBDA * elapsedMs.toDouble()).toFloat()
            val weightedPoints = event.eventType.baseWeight() * decay

            val normArtist = TrackMetadataNormalizer.normalizeArtist(event.artist)
            if (normArtist.isNotBlank()) {
                rawArtistPoints[normArtist] = (rawArtistPoints[normArtist] ?: 0f) + weightedPoints
            }

            event.genre?.let { g ->
                val normGenre = g.trim().lowercase()
                if (normGenre.isNotBlank()) {
                    rawGenrePoints[normGenre] = (rawGenrePoints[normGenre] ?: 0f) + weightedPoints
                }
            }

            event.mood?.let { m ->
                val normMood = m.trim().lowercase()
                if (normMood.isNotBlank()) {
                    rawMoodPoints[normMood] = (rawMoodPoints[normMood] ?: 0f) + weightedPoints
                }
            }

            event.language?.let { l ->
                val normLang = l.trim().lowercase()
                if (normLang.isNotBlank()) {
                    rawLanguagePoints[normLang] = (rawLanguagePoints[normLang] ?: 0f) + weightedPoints
                }
            }

            // Track recent session artists and skips
            val isRecentSession = event.timestamp >= recentCutoff || distinctSessions.contains(event.playbackSessionId)
            if (isRecentSession && normArtist.isNotBlank()) {
                if (event.eventType == EventType.SKIPPED_EARLY) {
                    recentSkippedArtists.add(normArtist)
                } else if (weightedPoints > 0) {
                    recentSessionArtists.add(normArtist)
                }
            }
        }

        // 2. Cold Start Bootstrapping from On-Device MediaStore Library
        if (localSongs.isNotEmpty()) {
            val artistCounts = localSongs.groupingBy { TrackMetadataNormalizer.normalizeArtist(it.artist) }.eachCount()
            for ((artist, count) in artistCounts) {
                if (artist.isBlank()) continue
                // Conservative boost: scales with track count up to 6 tracks
                val libraryBoost = min(count, 6) * RecommendationConfig.COLD_START_LOCAL_LIBRARY_BOOST
                rawArtistPoints[artist] = (rawArtistPoints[artist] ?: 0f) + libraryBoost
            }

            // Favorite songs in local library
            val favorites = localSongs.filter { it.isFavorite }
            for (fav in favorites) {
                val artist = TrackMetadataNormalizer.normalizeArtist(fav.artist)
                if (artist.isNotBlank()) {
                    rawArtistPoints[artist] = (rawArtistPoints[artist] ?: 0f) + RecommendationConfig.COLD_START_FAVORITE_BOOST
                }
            }
        }

        // 3. Cold Start Bootstrapping from Lyro Downloads
        for (dl in downloadedTracks) {
            val artist = TrackMetadataNormalizer.normalizeArtist(dl.artist)
            if (artist.isNotBlank()) {
                rawArtistPoints[artist] = (rawArtistPoints[artist] ?: 0f) + RecommendationConfig.COLD_START_DOWNLOAD_BOOST
            }
        }

        // 4. Normalize Points to [0.0, 1.0] Range using Soft Saturation
        val artistAffinities = normalizePoints(rawArtistPoints)
        val genreAffinities = normalizePoints(rawGenrePoints)
        val moodAffinities = normalizePoints(rawMoodPoints)
        val languageAffinities = normalizePoints(rawLanguagePoints)

        Log.d(
            TAG,
            "Computed TasteProfile: topArtists=${artistAffinities.toList().sortedByDescending { it.second }.take(5)}"
        )

        TasteProfile(
            artistAffinities = artistAffinities,
            genreAffinities = genreAffinities,
            moodAffinities = moodAffinities,
            languageAffinities = languageAffinities,
            recentSessionArtists = recentSessionArtists,
            recentSkippedArtists = recentSkippedArtists,
            calculatedAt = referenceTime
        )
    }

    /**
     * Normalizes raw score values into the [0.0, 1.0] interval.
     * Prevents a single play from dominating while rewarding repeated high engagement.
     */
    private fun normalizePoints(rawScores: Map<String, Float>): Map<String, Float> {
        val validPositiveScores = rawScores.filter { it.value > 0f }
        if (validPositiveScores.isEmpty()) return emptyMap()

        val peak = validPositiveScores.values.maxOrNull() ?: 1.0f
        val saturationCeiling = 20.0f
        val saturationFactor = min(1.0f, peak / saturationCeiling)

        val result = mutableMapOf<String, Float>()
        for ((key, points) in validPositiveScores) {
            val relative = (points / peak) * saturationFactor
            result[key] = relative.coerceIn(0.01f, 1.0f)
        }
        return result
    }
}
