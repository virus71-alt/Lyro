package com.lyro.app.core.matcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMetadataNormalizerTest {

    @Test
    fun testNormalizeTitle_stripsOfficialVideoAndJunk() {
        val raw = "Espresso (Official Music Video)"
        val normalized = TrackMetadataNormalizer.normalizeTitle(raw)
        assertEquals("espresso", normalized)

        val rawWithFeat = "Blinding Lights [Official Audio]"
        assertEquals("blinding lights", TrackMetadataNormalizer.normalizeTitle(rawWithFeat))
    }

    @Test
    fun testNormalizeArtist_stripsTopicAndVevo() {
        val artist = "Sabrina Carpenter - Topic"
        val normalized = TrackMetadataNormalizer.normalizeArtist(artist)
        assertEquals("sabrina carpenter", normalized)
    }

    @Test
    fun testMatches_identicalTracksMatch() {
        val matches = TrackMetadataNormalizer.matches(
            title1 = "Espresso (Official Video)",
            artist1 = "Sabrina Carpenter",
            duration1 = 180000L,
            title2 = "espresso",
            artist2 = "Sabrina Carpenter - Topic",
            duration2 = 182000L
        )
        assertTrue("Expected tracks with clean titles within tolerance to match", matches)
    }

    @Test
    fun testMatches_remixMustNotMatchOriginal() {
        val matches = TrackMetadataNormalizer.matches(
            title1 = "Espresso (Remix)",
            artist1 = "Sabrina Carpenter",
            duration1 = 180000L,
            title2 = "Espresso",
            artist2 = "Sabrina Carpenter",
            duration2 = 180000L
        )
        assertFalse("Remix should never match original", matches)
    }

    @Test
    fun testMatches_liveMustNotMatchStudio() {
        val matches = TrackMetadataNormalizer.matches(
            title1 = "Hotel California (Live)",
            artist1 = "Eagles",
            duration1 = 390000L,
            title2 = "Hotel California",
            artist2 = "Eagles",
            duration2 = 390000L
        )
        assertFalse("Live version should never match studio original", matches)
    }

    @Test
    fun testMatches_acousticMustNotMatchOriginal() {
        val matches = TrackMetadataNormalizer.matches(
            title1 = "Everlong - Acoustic",
            artist1 = "Foo Fighters",
            duration1 = 250000L,
            title2 = "Everlong",
            artist2 = "Foo Fighters",
            duration2 = 250000L
        )
        assertFalse("Acoustic version should never match original", matches)
    }

    @Test
    fun testMatches_durationExceedingToleranceFails() {
        val matches = TrackMetadataNormalizer.matches(
            title1 = "Espresso",
            artist1 = "Sabrina Carpenter",
            duration1 = 180000L,
            title2 = "Espresso",
            artist2 = "Sabrina Carpenter",
            duration2 = 205000L, // 25s difference > 10s tolerance
            toleranceMs = 10000L
        )
        assertFalse("Tracks differing by more than duration tolerance should not match", matches)
    }
}
