package com.lyro.app.recommendation.radio

import com.lyro.app.core.matcher.TrackMetadataNormalizer
import com.lyro.app.data.model.PlayableTrack
import java.util.UUID

/**
 * Encapsulates an active Lyro Radio session.
 * Tracks seed metadata, generated IDs to prevent duplication, live skip reactions,
 * and extension batch count (for seed influence decay).
 */
data class RadioSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val radioType: RadioType = RadioType.SONG,
    val seedTrack: PlayableTrack,
    val seedTrackId: String = seedTrack.id,
    val seedVideoId: String? = seedTrack.onlineVideoId,
    val seedArtist: String = seedTrack.artist,
    val seedTitle: String = seedTrack.title,
    val seedArtworkUri: String? = seedTrack.artworkUriString,
    val startedAt: Long = System.currentTimeMillis(),
    val generatedTrackIds: MutableSet<String> = mutableSetOf(),
    val recentlySkippedIds: MutableSet<String> = mutableSetOf(),
    val recentlySkippedArtists: MutableSet<String> = mutableSetOf(),
    var totalGeneratedCount: Int = 0,
    var extensionBatchCount: Int = 0,
    var isActive: Boolean = true
) {
    init {
        // Record seed track as generated so it is never re-recommended in this session
        generatedTrackIds.add(seedTrackId)
        seedVideoId?.let { generatedTrackIds.add(it) }
    }

    /**
     * Records an early skip during this radio session to penalize similar candidates in upcoming batches.
     */
    fun recordSkip(track: PlayableTrack) {
        val vid = track.onlineVideoId ?: track.id
        recentlySkippedIds.add(vid)
        recentlySkippedIds.add(track.id)
        if (track.artist.isNotBlank()) {
            recentlySkippedArtists.add(TrackMetadataNormalizer.normalizeArtist(track.artist))
        }
    }

    /**
     * Records a batch of generated tracks to prevent repetition.
     */
    fun recordGeneratedTracks(tracks: List<PlayableTrack>) {
        for (track in tracks) {
            generatedTrackIds.add(track.id)
            track.onlineVideoId?.let { generatedTrackIds.add(it) }
        }
        totalGeneratedCount += tracks.size
        extensionBatchCount++
    }
}
