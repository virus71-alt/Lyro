package com.lyro.app.core.smartdownload

import com.lyro.app.data.download.DownloadOrigin
import com.lyro.app.data.local.DownloadedMetadata
import com.lyro.app.recommendation.model.TasteProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SmartDownloadRetentionTest {

    private lateinit var evaluator: SmartDownloadRetentionEvaluator

    @Before
    fun setUp() {
        evaluator = SmartDownloadRetentionEvaluator()
    }

    @Test
    fun testLikedTrackReceivesSignificantBonus() {
        val now = System.currentTimeMillis()
        val meta = DownloadedMetadata(
            videoId = "vid_liked",
            title = "Liked Song",
            artist = "Artist A",
            album = "Album A",
            thumbnailUri = "",
            durationMs = 180000,
            localUri = "/data/music/liked.mp3",
            downloadOrigin = DownloadOrigin.SMART,
            fileSizeBytes = 3000000L,
            downloadedAt = now - 5L * 24 * 60 * 60 * 1000, // 5 days ago
            lastPlayedAt = now - 1L * 24 * 60 * 60 * 1000, // 1 day ago
            recommendationScore = 0.70f
        )

        val profile = TasteProfile(
            artistAffinities = mapOf("Artist A" to 0.8f)
        )

        val scoreLiked = evaluator.calculateRetentionScore(meta, profile, isLiked = true)
        val scoreUnliked = evaluator.calculateRetentionScore(meta, profile, isLiked = false)

        assertTrue("Liked track score ($scoreLiked) must be significantly higher than unliked ($scoreUnliked)", scoreLiked > scoreUnliked + 0.35f)
        assertTrue("Liked track should have very high retention", scoreLiked > 0.85f)
    }

    @Test
    fun testThirtyDayStaleUnplayedTrackReceivesPenalties() {
        val now = System.currentTimeMillis()
        val thirtyFiveDaysAgo = now - 35L * 24 * 60 * 60 * 1000

        val staleMeta = DownloadedMetadata(
            videoId = "vid_stale",
            title = "Forgotten Song",
            artist = "Unknown Artist",
            album = "Old Album",
            thumbnailUri = "",
            durationMs = 200000,
            localUri = "/data/music/stale.mp3",
            downloadOrigin = DownloadOrigin.SMART,
            fileSizeBytes = 3500000L,
            downloadedAt = thirtyFiveDaysAgo,
            lastPlayedAt = 0L, // Never played!
            recommendationScore = 0.20f
        )

        val profile = TasteProfile(artistAffinities = emptyMap())

        val score = evaluator.calculateRetentionScore(staleMeta, profile, isLiked = false)

        assertTrue("Stale unplayed track should have a very low retention score", score < 0.25f)
    }

    @Test
    fun testThirtyDayPlayedAndLikedTrackIsRetained() {
        val now = System.currentTimeMillis()
        val fortyDaysAgo = now - 40L * 24 * 60 * 60 * 1000
        val twoDaysAgo = now - 2L * 24 * 60 * 60 * 1000

        val evergreenMeta = DownloadedMetadata(
            videoId = "vid_evergreen",
            title = "Timeless Favorite",
            artist = "Favorite Artist",
            album = "Classic Album",
            thumbnailUri = "",
            durationMs = 240000,
            localUri = "/data/music/evergreen.mp3",
            downloadOrigin = DownloadOrigin.SMART,
            fileSizeBytes = 4000000L,
            downloadedAt = fortyDaysAgo,
            lastPlayedAt = twoDaysAgo, // Listened recently!
            recommendationScore = 0.85f
        )

        val profile = TasteProfile(artistAffinities = mapOf("Favorite Artist" to 0.9f))

        val score = evaluator.calculateRetentionScore(evergreenMeta, profile, isLiked = true)

        assertTrue("Old track that is liked and recently played must be retained with high score (got $score)", score > 0.80f)
    }

    @Test
    fun testHysteresisPreventsUnnecessaryChurn() {
        // Hysteresis requires newScore >= oldScore + 0.15
        val oldRetention = 0.70f

        // Candidate marginally better (0.75 vs 0.70)
        assertFalse("Marginal improvement should NOT replace existing smart download", evaluator.shouldReplace(newCandidateScore = 0.75f, existingRetentionScore = oldRetention))

        // Candidate 0.14 better (still under 0.15 threshold)
        assertFalse("Below threshold difference should not trigger rotation", evaluator.shouldReplace(newCandidateScore = 0.84f, existingRetentionScore = oldRetention))

        // Candidate 0.16 better (exceeds 0.15 threshold)
        assertTrue("Meaningful improvement (+0.16) should trigger rotation", evaluator.shouldReplace(newCandidateScore = 0.86f, existingRetentionScore = oldRetention))
    }

    @Test
    fun testPlaybackAndQueueSafetyPreventsEvictingActiveTracks() {
        val metaPlaying = DownloadedMetadata(
            videoId = "vid_active_play",
            title = "Currently Playing",
            artist = "Artist",
            album = "",
            thumbnailUri = "",
            durationMs = 180000,
            localUri = "/data/play.mp3",
            downloadOrigin = DownloadOrigin.SMART
        )

        val metaQueue = DownloadedMetadata(
            videoId = "vid_queued",
            title = "Next in Queue",
            artist = "Artist",
            album = "",
            thumbnailUri = "",
            durationMs = 180000,
            localUri = "/data/queue.mp3",
            downloadOrigin = DownloadOrigin.SMART
        )

        val metaInactive = DownloadedMetadata(
            videoId = "vid_other",
            title = "Idle Track",
            artist = "Artist",
            album = "",
            thumbnailUri = "",
            durationMs = 180000,
            localUri = "/data/other.mp3",
            downloadOrigin = DownloadOrigin.SMART
        )

        val activeTrackId = "vid_active_play"
        val queueIds = setOf("vid_queued", "vid_other_queue")

        assertFalse("Currently playing track must NEVER be evictable", evaluator.isSafeToEvict(metaPlaying, activeTrackId, queueIds))
        assertFalse("Queued track must NEVER be evictable", evaluator.isSafeToEvict(metaQueue, activeTrackId, queueIds))
        assertTrue("Inactive track should be safe to evict", evaluator.isSafeToEvict(metaInactive, activeTrackId, queueIds))
    }
}
