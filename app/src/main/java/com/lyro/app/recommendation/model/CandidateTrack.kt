package com.lyro.app.recommendation.model

import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.PlayableTrack

/**
 * Reason/Source tag assigned to candidate tracks during generation and ranking.
 * Used internally for transparent debugging and analytics.
 */
enum class RecommendationReason {
    TOP_ARTIST,
    SIMILAR_ARTIST,
    LANGUAGE_MATCH,
    GENRE_MATCH,
    MOOD_MATCH,
    RECENT_SESSION,
    EXPLORATION,
    COLD_START_FALLBACK
}

/**
 * Internal candidate representation holding recommendation metadata, source reason,
 * and ranking scores.
 */
data class CandidateTrack(
    val track: PlayableTrack,
    val reason: RecommendationReason,
    val score: Float = 0f,
    val isFamiliar: Boolean = false,
    val isAdjacent: Boolean = false,
    val isExploration: Boolean = false
) {
    val videoId: String = track.onlineVideoId ?: track.id
    val title: String = track.title
    val artist: String = track.artist
    val album: String? = track.album
}
