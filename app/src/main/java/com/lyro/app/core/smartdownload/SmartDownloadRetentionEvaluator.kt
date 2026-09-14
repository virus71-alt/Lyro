package com.lyro.app.core.smartdownload

import com.lyro.app.data.download.DownloadOrigin
import com.lyro.app.data.local.DownloadedMetadata
import com.lyro.app.recommendation.model.TasteProfile

/**
 * Evaluates the retention priority of existing Smart Downloads.
 * Implements the rolling 30-day retention model, liked-song protection,
 * play-frequency bonuses, and churn-prevention hysteresis.
 */
class SmartDownloadRetentionEvaluator {

    companion object {
        const val HYSTERESIS_MARGIN = 0.15f
        const val COOLDOWN_DAYS = 14L
        const val COOLDOWN_MS = COOLDOWN_DAYS * 24L * 60L * 60L * 1000L
    }

    /**
     * Calculates the retention score for an existing smart download.
     * Higher score = stronger reason to keep track offline.
     */
    fun calculateRetentionScore(
        meta: DownloadedMetadata,
        tasteProfile: TasteProfile,
        isLiked: Boolean
    ): Float {
        // 1. Current recommendation affinity
        val artistAffinity = tasteProfile.getArtistAffinity(meta.artist).takeIf { it > 0f }
            ?: meta.recommendationScore
        val baseAffinity = artistAffinity.coerceIn(0f, 1f)

        // 2. User liked status heavily protects tracks from removal
        val likeBonus = if (isLiked) 0.50f else 0.0f

        // 3. Playback recency bonus
        val now = System.currentTimeMillis()
        val daysSinceDownloaded = ((now - meta.downloadedAt) / (1000L * 60 * 60 * 24)).coerceAtLeast(0)
        val daysSincePlayed = if (meta.lastPlayedAt > 0L) {
            ((now - meta.lastPlayedAt) / (1000L * 60 * 60 * 24)).coerceAtLeast(0)
        } else {
            daysSinceDownloaded
        }

        val recentPlayBonus = when {
            meta.lastPlayedAt == 0L -> 0.0f
            daysSincePlayed <= 3 -> 0.35f
            daysSincePlayed <= 7 -> 0.25f
            daysSincePlayed <= 14 -> 0.15f
            daysSincePlayed <= 30 -> 0.05f
            else -> 0.0f
        }

        // 4. Rolling 30-day age and inactivity penalties
        val neverPlayedPenalty = if (meta.lastPlayedAt == 0L && daysSinceDownloaded >= 14) 0.25f else 0.0f
        val staleAgePenalty = if (daysSinceDownloaded >= 30 && daysSincePlayed >= 30) 0.30f else 0.0f

        return ((baseAffinity * 0.40f) + likeBonus + recentPlayBonus - neverPlayedPenalty - staleAgePenalty)
            .coerceIn(-1.0f, 2.0f)
    }

    /**
     * Determines whether a new candidate provides enough improvement to replace an existing track.
     * Prevents churn by requiring score improvement of at least [HYSTERESIS_MARGIN].
     */
    fun shouldReplace(newCandidateScore: Float, existingRetentionScore: Float): Boolean {
        return newCandidateScore >= (existingRetentionScore + HYSTERESIS_MARGIN)
    }

    /**
     * Verifies that the track is safe to delete right now.
     * NEVER deletes manual downloads, currently playing tracks, or immediate queued tracks.
     */
    fun isSafeToEvict(
        meta: DownloadedMetadata,
        currentPlayingId: String?,
        activeQueueIds: Set<String>
    ): Boolean {
        if (meta.downloadOrigin != DownloadOrigin.SMART) {
            return false // Manual downloads can NEVER be auto-deleted!
        }
        val cleanId = meta.videoId.removePrefix("online_")
        if (!currentPlayingId.isNullOrBlank() && currentPlayingId.removePrefix("online_") == cleanId) {
            return false // Playing right now
        }
        if (activeQueueIds.any { it.removePrefix("online_") == cleanId }) {
            return false // In current playback queue
        }
        return true
    }
}
