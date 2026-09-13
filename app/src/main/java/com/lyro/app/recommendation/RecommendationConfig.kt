package com.lyro.app.recommendation

/**
 * Centralized, configurable weights, thresholds, and hyperparameters for Lyro's
 * on-device personalized recommendation subsystem.
 *
 * All numeric values and constants are maintained here to avoid magic numbers scattered across the app.
 * Note: No hardcoded artists or genres belong here.
 */
object RecommendationConfig {

    // --- Signal Weights ---
    const val WEIGHT_LIKE = 8.0f
    const val WEIGHT_DOWNLOAD = 7.0f
    const val WEIGHT_REPLAY = 6.0f
    const val WEIGHT_PLAY_COMPLETED = 6.0f
    const val WEIGHT_PLAY_80_PERCENT = 5.0f
    const val WEIGHT_PLAY_50_PERCENT = 3.0f
    const val WEIGHT_SEARCHED_AND_PLAYED = 4.0f
    const val WEIGHT_ADD_TO_QUEUE = 3.0f
    const val WEIGHT_MANUAL_PLAY = 2.0f
    const val WEIGHT_PLAY_30_PERCENT = 1.0f

    // Negative Signal Weights
    const val WEIGHT_SKIP_BEFORE_15_SEC = -4.0f
    const val WEIGHT_SKIP_BEFORE_30_PCT = -2.0f
    const val WEIGHT_NOT_INTERESTED = -10.0f

    // --- Cold Start Bootstrapping Weights ---
    const val COLD_START_FAVORITE_BOOST = 6.0f
    const val COLD_START_DOWNLOAD_BOOST = 5.0f
    const val COLD_START_LOCAL_LIBRARY_BOOST = 1.5f // Conservative per track for local MediaStore

    // --- Time Decay Configuration ---
    // Half-life in days: after this time, an event's influence is halved
    const val HALF_LIFE_DAYS = 21.0
    // Lambda calculation: ln(2) / (half_life_days * millis_per_day)
    val DECAY_LAMBDA: Double = Math.log(2.0) / (HALF_LIFE_DAYS * 24.0 * 60.0 * 60.0 * 1000.0)

    // Short-term session taste window (in milliseconds: e.g. last 2 hours or last 5 sessions)
    const val RECENT_SESSION_WINDOW_MS = 2 * 60 * 60 * 1000L
    const val MAX_RECENT_SESSIONS_COUNT = 5

    // --- Recommendation Mix Ratios ---
    const val FAMILIAR_RATIO = 0.75f
    const val ADJACENT_RATIO = 0.15f
    const val EXPLORATION_RATIO = 0.10f

    // --- Diversity & Repetition Constraints ---
    const val MAX_TRACKS_PER_ARTIST = 3
    const val MAX_TRACKS_PER_ALBUM = 2

    // --- Ranking Factor Weights (Sums roughly to 1.0) ---
    const val RANK_ARTIST_WEIGHT = 0.40f
    const val RANK_GENRE_WEIGHT = 0.15f
    const val RANK_LANGUAGE_WEIGHT = 0.15f
    const val RANK_MOOD_WEIGHT = 0.10f
    const val RANK_SESSION_WEIGHT = 0.10f
    const val RANK_NOVELTY_BONUS = 0.10f

    // Penalties in Ranking
    const val PENALTY_RECENTLY_SKIPPED = 0.35f
    const val PENALTY_OVERPLAYED = 0.20f
    const val PENALTY_RECENTLY_SHOWN = 0.25f

    // Overplay threshold: if played more than this in recent sessions without a like
    const val OVERPLAY_PLAY_COUNT_THRESHOLD = 5

    // Candidate Generation Pool Sizes
    const val CANDIDATE_POOL_SIZE = 80
    const val TOP_AFFINITY_ARTISTS_TO_QUERY = 5

    // DB Bounding Limits
    const val MAX_PERSISTED_EVENTS = 2000
    const val MAX_EVENT_AGE_DAYS = 60
    const val MAX_RECENT_SHOWN_HISTORY = 100
}
