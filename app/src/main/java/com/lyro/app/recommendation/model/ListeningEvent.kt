package com.lyro.app.recommendation.model

import com.lyro.app.recommendation.RecommendationConfig

/**
 * Types of interactions captured by the local-first recommendation brain.
 */
enum class EventType {
    PLAY_STARTED,
    PLAY_30_PERCENT,
    PLAY_50_PERCENT,
    PLAY_80_PERCENT,
    PLAY_COMPLETED,
    SKIPPED_EARLY,
    REPLAYED,
    LIKED,
    UNLIKED,
    DOWNLOADED,
    ADDED_TO_QUEUE,
    SEARCHED_AND_PLAYED,
    MANUAL_PLAY,
    AUTO_PLAY,
    NOT_INTERESTED;

    /**
     * Resolves the configured numeric weight for this interaction.
     */
    fun baseWeight(): Float = when (this) {
        PLAY_STARTED -> 0.5f
        PLAY_30_PERCENT -> RecommendationConfig.WEIGHT_PLAY_30_PERCENT
        PLAY_50_PERCENT -> RecommendationConfig.WEIGHT_PLAY_50_PERCENT
        PLAY_80_PERCENT -> RecommendationConfig.WEIGHT_PLAY_80_PERCENT
        PLAY_COMPLETED -> RecommendationConfig.WEIGHT_PLAY_COMPLETED
        SKIPPED_EARLY -> RecommendationConfig.WEIGHT_SKIP_BEFORE_15_SEC
        REPLAYED -> RecommendationConfig.WEIGHT_REPLAY
        LIKED -> RecommendationConfig.WEIGHT_LIKE
        UNLIKED -> -RecommendationConfig.WEIGHT_LIKE / 2f
        DOWNLOADED -> RecommendationConfig.WEIGHT_DOWNLOAD
        ADDED_TO_QUEUE -> RecommendationConfig.WEIGHT_ADD_TO_QUEUE
        SEARCHED_AND_PLAYED -> RecommendationConfig.WEIGHT_SEARCHED_AND_PLAYED
        MANUAL_PLAY -> RecommendationConfig.WEIGHT_MANUAL_PLAY
        AUTO_PLAY -> 0.0f
        NOT_INTERESTED -> RecommendationConfig.WEIGHT_NOT_INTERESTED
    }
}

/**
 * A persistent or in-flight interaction record representing a meaningful milestone in music consumption.
 * Stored locally on-device in SQLite.
 */
data class ListeningEvent(
    val id: Long = 0L,
    val playbackSessionId: String,
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val eventType: EventType,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val percentageListened: Float = 0f,
    val isManual: Boolean = true,
    val language: String? = null,
    val genre: String? = null,
    val mood: String? = null
)
