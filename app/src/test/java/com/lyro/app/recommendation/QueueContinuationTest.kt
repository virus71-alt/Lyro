package com.lyro.app.recommendation

import com.lyro.app.data.model.LocalTrack
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.data.model.Song
import com.lyro.app.service.QueueContinuationManager
import org.junit.Assert.*
import org.junit.Test

class QueueContinuationTest {

    @Test
    fun testContinuationConstants() {
        assertEquals(15, QueueContinuationManager.BATCH_SIZE)
        assertEquals(4, QueueContinuationManager.EXTENSION_THRESHOLD)
        assertEquals(150, QueueContinuationManager.MAX_QUEUE_SIZE)
    }

    @Test
    fun testQueueDeduplicationLogic() {
        val song101 = Song(
            id = 101L,
            title = "Track 3",
            artist = "Artist C",
            album = "Album C",
            albumId = 3L,
            duration = 180000,
            contentUriString = "content://media/external/audio/media/101",
            albumArtUriString = null,
            size = 0,
            dateAdded = 0
        )

        val existingQueue: List<PlayableTrack> = listOf(
            OnlineTrack(videoId = "vid_1", title = "Track 1", artist = "Artist A"),
            OnlineTrack(videoId = "vid_2", title = "Track 2", artist = "Artist B"),
            LocalTrack(song = song101)
        )

        val existingIds = existingQueue.mapNotNull { it.onlineVideoId }.toSet() +
                existingQueue.map { it.id }.toSet() +
                existingQueue.map { "online_${it.onlineVideoId}" }.toSet()

        val candidates: List<PlayableTrack> = listOf(
            OnlineTrack(videoId = "vid_1", title = "Track 1 (Dupe)", artist = "Artist A"),
            OnlineTrack(videoId = "vid_3", title = "Track 4 (New)", artist = "Artist D"),
            LocalTrack(song = song101),
            OnlineTrack(videoId = "vid_4", title = "Track 5 (New)", artist = "Artist E")
        )

        val filtered = candidates.filter { track ->
            !existingIds.contains(track.id) &&
            (track.onlineVideoId == null || (!existingIds.contains(track.onlineVideoId) && !existingIds.contains("online_${track.onlineVideoId}")))
        }

        assertEquals(2, filtered.size)
        assertEquals("vid_3", filtered[0].onlineVideoId)
        assertEquals("vid_4", filtered[1].onlineVideoId)
    }

    @Test
    fun testScrollNearBottomThresholdCalculation() {
        val totalItems = 20
        val lastVisibleIndex1 = 14
        val shouldFetch1 = (totalItems > 0 && lastVisibleIndex1 >= totalItems - 5)
        assertFalse("At index 14 out of 20, remaining is 6 -> should not fetch yet", shouldFetch1)

        val lastVisibleIndex2 = 15
        val shouldFetch2 = (totalItems > 0 && lastVisibleIndex2 >= totalItems - 5)
        assertTrue("At index 15 out of 20, remaining is 5 -> should trigger prefetch", shouldFetch2)

        val lastVisibleIndex3 = 19
        val shouldFetch3 = (totalItems > 0 && lastVisibleIndex3 >= totalItems - 5)
        assertTrue("At index 19 out of 20, remaining is 1 -> should trigger prefetch", shouldFetch3)
    }

    @Test
    fun testPlaybackSafetyPrefetchThresholdCalculation() {
        val queueSize = 12
        val threshold = QueueContinuationManager.EXTENSION_THRESHOLD // 4

        val currentIndexEarly = 7
        val shouldPrefetchEarly = (currentIndexEarly >= queueSize - threshold)
        assertFalse("At index 7/12 (5 remaining), playback safety should not fire yet", shouldPrefetchEarly)

        val currentIndexNearEnd = 8
        val shouldPrefetchNearEnd = (currentIndexNearEnd >= queueSize - threshold)
        assertTrue("At index 8/12 (4 remaining), playback safety prefetch must fire", shouldPrefetchNearEnd)
    }
}
