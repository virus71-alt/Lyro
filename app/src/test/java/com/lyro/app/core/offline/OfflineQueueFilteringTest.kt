package com.lyro.app.core.offline

import com.lyro.app.core.matcher.LocalMediaIndex
import com.lyro.app.data.local.DownloadedMetadata
import com.lyro.app.data.model.LocalTrack
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.data.model.Song
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class OfflineQueueFilteringTest {

    private lateinit var localMediaIndex: LocalMediaIndex
    private lateinit var resolver: OfflineAvailabilityResolver
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        localMediaIndex = LocalMediaIndex()
        resolver = OfflineAvailabilityResolver(
            context = null,
            localMediaIndex = localMediaIndex,
            musicDownloader = null,
            dbHelper = null
        )
    }

    @After
    fun tearDown() {
        for (f in tempFiles) {
            try { if (f.exists()) f.delete() } catch (_: Exception) {}
        }
        tempFiles.clear()
    }

    private fun createAudioFile(tag: String): File {
        val f = File.createTempFile("queue_test_$tag", ".mp3")
        f.writeBytes(ByteArray(512) { 0x11.toByte() })
        tempFiles.add(f)
        return f
    }

    private fun createLocalTrack(id: Long, title: String): LocalTrack {
        val file = createAudioFile(title)
        val song = Song(
            id = id,
            title = title,
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            duration = 180000,
            contentUriString = file.absolutePath,
            albumArtUriString = null,
            size = file.length(),
            dateAdded = System.currentTimeMillis()
        )
        return LocalTrack(song)
    }

    private fun createDownloadedOnlineTrack(videoId: String, title: String): OnlineTrack {
        val file = createAudioFile(videoId)
        val meta = DownloadedMetadata(
            videoId = videoId,
            displayName = title,
            title = title,
            artist = "Online Artist",
            album = "Single",
            thumbnailUri = null,
            durationMs = 200000,
            localUri = file.absolutePath
        )
        localMediaIndex.rebuild(emptyList(), listOf(meta))
        return OnlineTrack(videoId = videoId, title = title, artist = "Online Artist")
    }

    private fun createOnlineOnlyTrack(videoId: String, title: String): OnlineTrack {
        return OnlineTrack(videoId = videoId, title = title, artist = "Streaming Artist")
    }

    @Test
    fun testOfflineQueueFiltersOutOnlineTracksAndRecalculatesIndex() {
        // Queue: A (downloaded), B (online-only), C (local), D (online-only), E (local)
        val trackA = createDownloadedOnlineTrack("vid_a", "Track A")
        val trackB = createOnlineOnlyTrack("vid_b", "Track B")
        val trackC = createLocalTrack(103L, "Track C")
        val trackD = createOnlineOnlyTrack("vid_d", "Track D")
        val trackE = createLocalTrack(105L, "Track E")

        val mixedQueue = listOf<PlayableTrack>(trackA, trackB, trackC, trackD, trackE)

        // Starting track is track C (index 2 in mixedQueue)
        val initialSelectedTrack = trackC

        // Simulate offline queue preparation
        val offlineQueue = mixedQueue.filter { resolver.isAvailableOffline(it) }

        assertEquals(3, offlineQueue.size)
        assertEquals("Track A", offlineQueue[0].title)
        assertEquals("Track C", offlineQueue[1].title)
        assertEquals("Track E", offlineQueue[2].title)

        // New index calculation
        val newIndex = offlineQueue.indexOfFirst { it.id == initialSelectedTrack.id }.coerceAtLeast(0)
        assertEquals(1, newIndex)
        assertEquals("Track C", offlineQueue[newIndex].title)
    }

    @Test
    fun testNetworkLostDuringPlaybackFiltersUpcomingTracksWithoutKillingCurrent() {
        val trackA = createLocalTrack(101L, "Track A")
        val trackB = createOnlineOnlyTrack("vid_b", "Track B")
        val trackC = createLocalTrack(103L, "Track C")
        val trackD = createOnlineOnlyTrack("vid_d", "Track D")

        val currentQueue = listOf<PlayableTrack>(trackA, trackB, trackC, trackD)
        val currentIndex = 0 // Track A is currently playing

        val currentPlayingTrack = currentQueue[currentIndex]

        // Network disconnected! Apply the same logic used in PlaybackManager:
        // Filter upcoming tracks (from currentIndex + 1 onwards)
        val head = currentQueue.take(currentIndex + 1)
        val upcomingFiltered = currentQueue.drop(currentIndex + 1).filter { resolver.isAvailableOffline(it) }
        val newQueue = head + upcomingFiltered

        assertEquals(2, newQueue.size)
        assertEquals(currentPlayingTrack.id, newQueue[currentIndex].id) // Track A still at index 0
        assertEquals("Track C", newQueue[1].title) // Next track is directly Track C, bypassing Track B

        // If user presses Next now, next track index is 1: Track C
        val nextTrack = newQueue.getOrNull(currentIndex + 1)
        assertNotNull(nextTrack)
        assertEquals("Track C", nextTrack?.title)
    }

    @Test
    fun testAllOnlineQueueResultsInEmptyOfflineQueue() {
        val b1 = createOnlineOnlyTrack("vid_1", "Stream 1")
        val b2 = createOnlineOnlyTrack("vid_2", "Stream 2")
        val b3 = createOnlineOnlyTrack("vid_3", "Stream 3")

        val mixedQueue = listOf(b1, b2, b3)
        val offlineQueue = mixedQueue.filter { resolver.isAvailableOffline(it) }

        assertTrue("Queue with only online tracks should be completely empty offline", offlineQueue.isEmpty())
    }
}
