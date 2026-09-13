package com.lyro.app.recommendation.model

import com.lyro.app.data.model.PlayableTrack
import java.util.UUID

/**
 * Tracks the in-memory lifecycle of a single track playback session.
 * Guarantees that progress milestones (30%, 50%, 80%, completed) fire at most once per session.
 */
class PlaybackSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val track: PlayableTrack,
    val isManual: Boolean = true,
    val playSource: String = "home",
    val startTime: Long = System.currentTimeMillis(),
    val language: String? = null,
    val genre: String? = null,
    val mood: String? = null
) {
    val videoId: String = track.onlineVideoId ?: track.id
    val title: String = track.title
    val artist: String = track.artist
    val album: String? = track.album

    private val firedMilestones = mutableSetOf<EventType>()

    var lastPositionMs: Long = 0L
        private set

    var durationMs: Long = track.durationMs
        private set

    var maxPercentageListened: Float = 0f
        private set

    fun updateDuration(duration: Long) {
        if (duration > 0L) {
            durationMs = duration
        }
    }

    /**
     * Evaluates the current playback position against milestone thresholds (30%, 50%, 80%).
     * Returns newly reached milestone events to persist.
     */
    fun checkMilestones(positionMs: Long, totalDurationMs: Long = durationMs): List<ListeningEvent> {
        lastPositionMs = positionMs
        if (totalDurationMs > 0L) {
            durationMs = totalDurationMs
        }
        if (durationMs <= 0L) return emptyList()

        val percentage = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        if (percentage > maxPercentageListened) {
            maxPercentageListened = percentage
        }

        val events = mutableListOf<ListeningEvent>()

        if (percentage >= 0.30f && firedMilestones.add(EventType.PLAY_30_PERCENT)) {
            events.add(createEvent(EventType.PLAY_30_PERCENT, positionMs, percentage))
        }
        if (percentage >= 0.50f && firedMilestones.add(EventType.PLAY_50_PERCENT)) {
            events.add(createEvent(EventType.PLAY_50_PERCENT, positionMs, percentage))
        }
        if (percentage >= 0.80f && firedMilestones.add(EventType.PLAY_80_PERCENT)) {
            events.add(createEvent(EventType.PLAY_80_PERCENT, positionMs, percentage))
        }

        return events
    }

    /**
     * Called when the user manually initiates Next or navigates away.
     * If the track was skipped early (< 15 seconds or < 30%), a negative skip event is generated.
     */
    fun onManualSkip(): ListeningEvent? {
        val percentage = if (durationMs > 0L) (lastPositionMs.toFloat() / durationMs.toFloat()) else 0f
        val isEarly = lastPositionMs < 15_000L || percentage < 0.30f

        return if (isEarly && firedMilestones.add(EventType.SKIPPED_EARLY)) {
            createEvent(EventType.SKIPPED_EARLY, lastPositionMs, percentage)
        } else {
            null
        }
    }

    /**
     * Called when a track naturally reaches completion (Player.STATE_ENDED).
     */
    fun onCompleted(): ListeningEvent? {
        if (firedMilestones.add(EventType.PLAY_COMPLETED)) {
            return createEvent(EventType.PLAY_COMPLETED, durationMs, 1.0f)
        }
        return null
    }

    private fun createEvent(
        type: EventType,
        posMs: Long,
        pct: Float
    ): ListeningEvent = ListeningEvent(
        playbackSessionId = sessionId,
        videoId = videoId,
        title = title,
        artist = artist,
        album = album,
        eventType = type,
        timestamp = System.currentTimeMillis(),
        durationMs = durationMs,
        positionMs = posMs,
        percentageListened = pct,
        isManual = isManual,
        language = language,
        genre = genre,
        mood = mood
    )
}
