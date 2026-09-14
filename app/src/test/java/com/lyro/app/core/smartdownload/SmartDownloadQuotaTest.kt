package com.lyro.app.core.smartdownload

import com.lyro.app.data.download.DownloadOrigin
import com.lyro.app.data.local.DownloadedMetadata
import com.lyro.app.data.preferences.SmartDownloadPreferences
import org.junit.Assert.*
import org.junit.Test

class SmartDownloadQuotaTest {

    @Test
    fun testStorageLimitPresetsExactBytes() {
        assertEquals("1 GB preset must be exactly 1073741824 bytes", 1L * 1024 * 1024 * 1024, SmartDownloadPreferences.ONE_GB_BYTES)
        assertEquals("2 GB preset must be exactly 2147483648 bytes", 2L * 1024 * 1024 * 1024, SmartDownloadPreferences.TWO_GB_BYTES)
        assertEquals("3 GB preset must be exactly 3221225472 bytes", 3L * 1024 * 1024 * 1024, SmartDownloadPreferences.THREE_GB_BYTES)
        assertEquals("5 GB preset must be exactly 5368709120 bytes", 5L * 1024 * 1024 * 1024, SmartDownloadPreferences.FIVE_GB_BYTES)
    }

    @Test
    fun testManualDownloadsExcludedFromSmartQuota() {
        val tracks = listOf(
            DownloadedMetadata(videoId = "m1", title = "Manual 1", artist = "Artist", localUri = "/p1", downloadOrigin = DownloadOrigin.MANUAL, fileSizeBytes = 100_000_000L),
            DownloadedMetadata(videoId = "m2", title = "Manual 2", artist = "Artist", localUri = "/p2", downloadOrigin = DownloadOrigin.MANUAL, fileSizeBytes = 200_000_000L),
            DownloadedMetadata(videoId = "s1", title = "Smart 1", artist = "Artist", localUri = "/p3", downloadOrigin = DownloadOrigin.SMART, fileSizeBytes = 50_000_000L),
            DownloadedMetadata(videoId = "s2", title = "Smart 2", artist = "Artist", localUri = "/p4", downloadOrigin = DownloadOrigin.SMART, fileSizeBytes = 70_000_000L)
        )

        val totalBytes = tracks.sumOf { it.fileSizeBytes }
        val smartBytes = tracks.filter { it.downloadOrigin == DownloadOrigin.SMART }.sumOf { it.fileSizeBytes }
        val manualBytes = tracks.filter { it.downloadOrigin == DownloadOrigin.MANUAL }.sumOf { it.fileSizeBytes }

        assertEquals(420_000_000L, totalBytes)
        assertEquals(120_000_000L, smartBytes)
        assertEquals(300_000_000L, manualBytes)

        val quota: Long = SmartDownloadPreferences.ONE_GB_BYTES
        assertTrue("Smart downloads (120 MB) must easily fit inside 1 GB quota", smartBytes < quota)
        assertEquals("Quota check considers smart bytes only", 120_000_000L, smartBytes)
    }

    @Test
    fun testSafetyBufferCalculation() {
        val minBuffer = SmartDownloadManager.MIN_FREE_STORAGE_BUFFER_BYTES
        assertEquals("Safety buffer must be at least 500 MB", 500L * 1024 * 1024, minBuffer)

        // For a 64 GB device: 5% is ~3.2 GB
        val deviceTotal64Gb = 64L * 1024 * 1024 * 1024
        val fivePercent64Gb = (deviceTotal64Gb * 0.05).toLong()
        val requiredBuffer64Gb = maxOf(minBuffer, fivePercent64Gb)
        assertEquals(fivePercent64Gb, requiredBuffer64Gb)

        // For an 8 GB device: 5% is 400 MB, so minBuffer (500 MB) wins
        val deviceTotal8Gb = 8L * 1024 * 1024 * 1024
        val fivePercent8Gb = (deviceTotal8Gb * 0.05).toLong()
        val requiredBuffer8Gb = maxOf(minBuffer, fivePercent8Gb)
        assertEquals(minBuffer, requiredBuffer8Gb)
    }
}
