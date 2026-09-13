package com.lyro.app.recommendation.model

import com.lyro.app.data.model.PlayableTrack

/**
 * Encapsulates the context for candidate ranking.
 * Allows RecommendationRanker to adapt its scoring behavior dynamically between
 * Home recommendations (long-term focus) and Radio (seed-focused with decay, session taste, and strict diversity).
 */
sealed class RecommendationContext {
    object Home : RecommendationContext()

    data class Radio(
        val seedTrack: PlayableTrack,
        val recentSessionTaste: Set<String> = emptySet(),
        val extensionBatchIndex: Int = 0,
        val sessionTrackIds: Set<String> = emptySet(),
        val sessionSkippedIds: Set<String> = emptySet(),
        val sessionSkippedArtists: Set<String> = emptySet()
    ) : RecommendationContext()
}
