package com.lyro.app.recommendation.model

import com.lyro.app.core.matcher.TrackMetadataNormalizer

/**
 * Encapsulates multidimensional affinities (artist, genre, mood, language).
 * Scores are normalized in the [0.0, 1.0] range.
 * Never exposes raw internal point values directly to UI.
 */
data class TasteProfile(
    val artistAffinities: Map<String, Float> = emptyMap(),
    val genreAffinities: Map<String, Float> = emptyMap(),
    val moodAffinities: Map<String, Float> = emptyMap(),
    val languageAffinities: Map<String, Float> = emptyMap(),
    val recentSessionArtists: Set<String> = emptySet(),
    val recentSkippedArtists: Set<String> = emptySet(),
    val calculatedAt: Long = System.currentTimeMillis()
) {
    val topArtists: List<Pair<String, Float>> by lazy {
        artistAffinities.toList().sortedByDescending { it.second }
    }

    val topGenres: List<Pair<String, Float>> by lazy {
        genreAffinities.toList().sortedByDescending { it.second }
    }

    val topLanguages: List<Pair<String, Float>> by lazy {
        languageAffinities.toList().sortedByDescending { it.second }
    }

    val topMoods: List<Pair<String, Float>> by lazy {
        moodAffinities.toList().sortedByDescending { it.second }
    }

    /**
     * Resolves normalized affinity for an artist name.
     * Uses TrackMetadataNormalizer to handle capitalization, trailing tags, etc.
     */
    fun getArtistAffinity(artist: String): Float {
        val normalized = TrackMetadataNormalizer.normalizeArtist(artist)
        return artistAffinities[normalized]
            ?: artistAffinities[artist.trim().lowercase()]
            ?: 0.0f
    }

    fun getGenreAffinity(genre: String?): Float {
        if (genre.isNullOrBlank()) return 0.0f
        return genreAffinities[genre.trim().lowercase()] ?: 0.0f
    }

    fun getLanguageAffinity(language: String?): Float {
        if (language.isNullOrBlank()) return 0.0f
        return languageAffinities[language.trim().lowercase()] ?: 0.0f
    }

    fun getMoodAffinity(mood: String?): Float {
        if (mood.isNullOrBlank()) return 0.0f
        return moodAffinities[mood.trim().lowercase()] ?: 0.0f
    }

    fun isArtistRecentlySkipped(artist: String): Boolean {
        val normalized = TrackMetadataNormalizer.normalizeArtist(artist)
        return recentSkippedArtists.contains(normalized) || recentSkippedArtists.contains(artist.trim().lowercase())
    }

    fun isArtistInRecentSession(artist: String): Boolean {
        val normalized = TrackMetadataNormalizer.normalizeArtist(artist)
        return recentSessionArtists.contains(normalized) || recentSessionArtists.contains(artist.trim().lowercase())
    }

    fun isEmpty(): Boolean {
        return artistAffinities.isEmpty() && genreAffinities.isEmpty() &&
                languageAffinities.isEmpty() && moodAffinities.isEmpty()
    }
}
