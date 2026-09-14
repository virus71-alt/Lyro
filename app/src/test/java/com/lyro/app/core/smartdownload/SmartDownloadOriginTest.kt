package com.lyro.app.core.smartdownload

import com.lyro.app.data.download.DownloadOrigin
import com.lyro.app.data.local.DownloadedMetadata
import org.junit.Assert.*
import org.junit.Test

class SmartDownloadOriginTest {

    @Test
    fun testDownloadOriginDistinction() {
        val manualMeta = DownloadedMetadata(
            videoId = "track_man",
            title = "Manual Track",
            artist = "Artist",
            album = "Album",
            thumbnailUri = "",
            durationMs = 180000,
            localUri = "/downloads/track_man.mp3",
            downloadOrigin = DownloadOrigin.MANUAL
        )

        val smartMeta = DownloadedMetadata(
            videoId = "track_smart",
            title = "Smart Track",
            artist = "Artist",
            album = "Album",
            thumbnailUri = "",
            durationMs = 180000,
            localUri = "/downloads/track_smart.mp3",
            downloadOrigin = DownloadOrigin.SMART
        )

        assertEquals("Manual track must have MANUAL origin", DownloadOrigin.MANUAL, manualMeta.downloadOrigin)
        assertEquals("Smart track must have SMART origin", DownloadOrigin.SMART, smartMeta.downloadOrigin)
        assertNotEquals("Origins must differ", manualMeta.downloadOrigin, smartMeta.downloadOrigin)
    }

    @Test
    fun testPromotionFromSmartToManual() {
        var track = DownloadedMetadata(
            videoId = "track_promote",
            title = "Promotable Track",
            artist = "Artist",
            album = "Album",
            thumbnailUri = "",
            durationMs = 180000,
            localUri = "/downloads/track_promote.mp3",
            downloadOrigin = DownloadOrigin.SMART
        )

        assertEquals("Initially smart", DownloadOrigin.SMART, track.downloadOrigin)

        // User taps Download / Keep offline -> Promoted to MANUAL
        track = track.copy(downloadOrigin = DownloadOrigin.MANUAL)

        assertEquals("After promotion must be MANUAL", DownloadOrigin.MANUAL, track.downloadOrigin)
    }

    @Test
    fun testSmartDownloadExclusionFilters() {
        val tracks = listOf(
            DownloadedMetadata(videoId = "id1", title = "Track 1", artist = "Artist", localUri = "/p1", downloadOrigin = DownloadOrigin.MANUAL),
            DownloadedMetadata(videoId = "id2", title = "Track 2", artist = "Artist", localUri = "/p2", downloadOrigin = DownloadOrigin.SMART),
            DownloadedMetadata(videoId = "id3", title = "Track 3", artist = "Artist", localUri = "/p3", downloadOrigin = DownloadOrigin.SMART),
            DownloadedMetadata(videoId = "id4", title = "Track 4", artist = "Artist", localUri = "/p4", downloadOrigin = DownloadOrigin.MANUAL)
        )

        val smartOnly = tracks.filter { it.downloadOrigin == DownloadOrigin.SMART }
        val manualOnly = tracks.filter { it.downloadOrigin == DownloadOrigin.MANUAL }

        assertEquals(2, smartOnly.size)
        assertEquals(2, manualOnly.size)

        // Simulating cleanup of smart downloads only
        val remainingAfterSmartCleanup = tracks.filter { it.downloadOrigin != DownloadOrigin.SMART }
        assertEquals(2, remainingAfterSmartCleanup.size)
        assertTrue("Manual tracks must never be deleted in smart cleanup", remainingAfterSmartCleanup.all { it.downloadOrigin == DownloadOrigin.MANUAL })
    }
}
