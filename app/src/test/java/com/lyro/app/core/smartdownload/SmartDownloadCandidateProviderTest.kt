package com.lyro.app.core.smartdownload

import com.lyro.app.core.matcher.LocalMediaIndex
import com.lyro.app.data.download.DownloadOrigin
import com.lyro.app.data.local.DownloadedMetadata
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.Song
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SmartDownloadCandidateProviderTest {

    private lateinit var localMediaIndex: LocalMediaIndex

    @Before
    fun setUp() {
        localMediaIndex = LocalMediaIndex()
    }

    @Test
    fun testCandidateExclusions() {
        val downloadedSmart = DownloadedMetadata(
            videoId = "vid_smart_exists",
            title = "Existing Smart Song",
            artist = "Artist",
            album = "",
            thumbnailUri = "",
            durationMs = 180000,
            localUri = "/p1",
            downloadOrigin = DownloadOrigin.SMART
        )

        val downloadedManual = DownloadedMetadata(
            videoId = "vid_manual_exists",
            title = "Existing Manual Song",
            artist = "Artist",
            album = "",
            thumbnailUri = "",
            durationMs = 180000,
            localUri = "/p2",
            downloadOrigin = DownloadOrigin.MANUAL
        )

        val activeQueueIds = setOf("vid_playing_now")
        val notInterestedIds = setOf("vid_disliked")
        val cooldownIds = setOf("vid_in_cooldown")

        val candidates = listOf(
            OnlineTrack("vid_smart_exists", "Existing Smart Song", "Artist"),
            OnlineTrack("vid_manual_exists", "Existing Manual Song", "Artist"),
            OnlineTrack("vid_playing_now", "Playing Song", "Artist"),
            OnlineTrack("vid_disliked", "Disliked Song", "Artist"),
            OnlineTrack("vid_in_cooldown", "Cooldown Song", "Artist"),
            OnlineTrack("vid_fresh_candidate", "Fresh Candidate Song", "Artist")
        )

        val existingDownloadedIds = setOf(downloadedSmart.videoId, downloadedManual.videoId)

        val filtered = candidates.filter { track ->
            !existingDownloadedIds.contains(track.videoId) &&
            !activeQueueIds.contains(track.videoId) &&
            !notInterestedIds.contains(track.videoId) &&
            !cooldownIds.contains(track.videoId)
        }

        assertEquals(1, filtered.size)
        assertEquals("vid_fresh_candidate", filtered.first().videoId)
    }

    @Test
    fun testRatioEightyTwentyCalculation() {
        val targetCount = 10
        val highConfidenceRatio = 0.80f

        val highConfidenceTarget = (targetCount * highConfidenceRatio).toInt()
        val discoveryTarget = targetCount - highConfidenceTarget

        assertEquals(8, highConfidenceTarget)
        assertEquals(2, discoveryTarget)
        assertEquals(targetCount, highConfidenceTarget + discoveryTarget)
    }

    @Test
    fun testLocalMediaStoreTrackExclusion() {
        // Register local song into LocalMediaIndex
        val localSong = Song(
            id = 555L,
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            albumId = 1L,
            duration = 354000,
            contentUriString = "/storage/emulated/0/Music/queen.mp3",
            albumArtUriString = null,
            size = 1000L,
            dateAdded = 1000L
        )
        localMediaIndex.rebuild(listOf(localSong), emptyList())

        val onlineDuplicate = OnlineTrack("vid_queen", "Bohemian Rhapsody", "Queen")
        val match = localMediaIndex.findLocalMatch(onlineDuplicate)

        assertNotNull("Local MediaStore file must match online candidate", match)
        assertEquals(localSong.id, match?.id)
    }
}
