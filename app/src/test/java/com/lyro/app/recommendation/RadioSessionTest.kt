package com.lyro.app.recommendation

import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.recommendation.engine.RecommendationRanker
import com.lyro.app.recommendation.model.CandidateTrack
import com.lyro.app.recommendation.model.RecommendationContext
import com.lyro.app.recommendation.model.RecommendationReason
import com.lyro.app.recommendation.model.TasteProfile
import com.lyro.app.recommendation.radio.RadioSession
import com.lyro.app.recommendation.radio.RadioType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RadioSessionTest {

    private lateinit var ranker: RecommendationRanker

    @Before
    fun setUp() {
        ranker = RecommendationRanker()
    }

    @Test
    fun testRadioSessionInitialization() {
        val seed = OnlineTrack(
            videoId = "seed_vid_123",
            title = "Seed Song",
            artist = "Seed Artist",
            album = "Seed Album"
        )
        val session = RadioSession(
            radioType = RadioType.SONG,
            seedTrack = seed
        )

        assertEquals(RadioType.SONG, session.radioType)
        assertEquals("Seed Song", session.seedTitle)
        assertEquals("Seed Artist", session.seedArtist)
        assertEquals("seed_vid_123", session.seedVideoId)
        assertTrue("Seed track ID must be in generatedTrackIds to prevent re-recommending", session.generatedTrackIds.contains("seed_vid_123"))
        assertTrue(session.isActive)
        assertEquals(0, session.extensionBatchCount)
    }

    @Test
    fun testRadioRankingSeedDecay() {
        val seed = OnlineTrack(videoId = "seed_1", title = "Seed Song", artist = "ArtistSeed")
        val tasteProfile = TasteProfile(
            artistAffinities = mapOf(
                "artistseed" to 0.10f,
                "artisttaste" to 0.90f
            )
        )

        val seedArtistCandidate = CandidateTrack(
            track = OnlineTrack(videoId = "seed_cand_1", title = "Seed Similar 1", artist = "ArtistSeed"),
            reason = RecommendationReason.SIMILAR_ARTIST
        )
        val tasteArtistCandidate = CandidateTrack(
            track = OnlineTrack(videoId = "taste_cand_1", title = "Taste Song 1", artist = "ArtistTaste"),
            reason = RecommendationReason.TOP_ARTIST
        )

        // Batch 0: high seed similarity weight
        val scoreSeedBatch0 = ranker.calculateScore(
            candidate = seedArtistCandidate,
            tasteProfile = tasteProfile,
            recentlyShownIds = emptySet(),
            overplayedIds = emptySet(),
            context = RecommendationContext.Radio(
                seedTrack = seed,
                extensionBatchIndex = 0
            )
        )

        // Batch 4: seed weight decayed
        val scoreSeedBatch4 = ranker.calculateScore(
            candidate = seedArtistCandidate,
            tasteProfile = tasteProfile,
            recentlyShownIds = emptySet(),
            overplayedIds = emptySet(),
            context = RecommendationContext.Radio(
                seedTrack = seed,
                extensionBatchIndex = 4
            )
        )

        val scoreTasteBatch4 = ranker.calculateScore(
            candidate = tasteArtistCandidate,
            tasteProfile = tasteProfile,
            recentlyShownIds = emptySet(),
            overplayedIds = emptySet(),
            context = RecommendationContext.Radio(
                seedTrack = seed,
                extensionBatchIndex = 4
            )
        )

        assertTrue(
            "Seed candidate score should decay from batch 0 ($scoreSeedBatch0) to batch 4 ($scoreSeedBatch4)",
            scoreSeedBatch0 > scoreSeedBatch4
        )
        assertTrue(
            "In later batches, long-term high affinity taste candidate ($scoreTasteBatch4) should become stronger relative to decayed seed candidate ($scoreSeedBatch4)",
            scoreTasteBatch4 > scoreSeedBatch4
        )
    }

    @Test
    fun testRadioArtistDiversityAndNoConsecutiveRepetition() {
        val seed = OnlineTrack(videoId = "seed_1", title = "Seed Song", artist = "ArtistSeed")
        val tasteProfile = TasteProfile(
            artistAffinities = mapOf(
                "artistseed" to 0.80f,
                "artistone" to 0.70f,
                "artisttwo" to 0.60f,
                "artistthree" to 0.50f
            )
        )

        // Generate multiple tracks per artist
        val seedTracks = (1..5).map { i ->
            CandidateTrack(
                track = OnlineTrack(videoId = "seed_cand_$i", title = "Seed Cand $i", artist = "ArtistSeed"),
                reason = RecommendationReason.SIMILAR_ARTIST
            )
        }
        val artistOneTracks = (1..4).map { i ->
            CandidateTrack(
                track = OnlineTrack(videoId = "one_cand_$i", title = "One Cand $i", artist = "ArtistOne"),
                reason = RecommendationReason.TOP_ARTIST
            )
        }
        val artistTwoTracks = (1..4).map { i ->
            CandidateTrack(
                track = OnlineTrack(videoId = "two_cand_$i", title = "Two Cand $i", artist = "ArtistTwo"),
                reason = RecommendationReason.TOP_ARTIST
            )
        }
        val artistThreeTracks = (1..4).map { i ->
            CandidateTrack(
                track = OnlineTrack(videoId = "three_cand_$i", title = "Three Cand $i", artist = "ArtistThree"),
                reason = RecommendationReason.TOP_ARTIST
            )
        }

        val all = seedTracks + artistOneTracks + artistTwoTracks + artistThreeTracks

        val ranked = ranker.rankCandidates(
            candidates = all,
            tasteProfile = tasteProfile,
            targetCount = 8,
            context = RecommendationContext.Radio(seedTrack = seed)
        )

        assertEquals("Should return requested count", 8, ranked.size)

        // 1. Check max per artist constraint for Radio (max 2)
        val seedCount = ranked.count { it.artist.equals("ArtistSeed", ignoreCase = true) }
        assertTrue("Radio must not contain more than RADIO_MAX_TRACKS_PER_ARTIST (2) by same artist, found $seedCount", seedCount <= 2)

        // 2. Check no immediate consecutive artist repetition
        for (i in 0 until ranked.size - 1) {
            assertNotEquals(
                "Adjacent radio tracks must not share the same artist: index $i and ${i + 1}",
                ranked[i].artist.lowercase(),
                ranked[i + 1].artist.lowercase()
            )
        }
    }

    @Test
    fun testRadioExcludesDuplicates() {
        val seed = OnlineTrack(videoId = "seed_1", title = "Seed Song", artist = "ArtistSeed")
        val tasteProfile = TasteProfile()

        val exactSeedCandidate = CandidateTrack(
            track = seed,
            reason = RecommendationReason.SIMILAR_ARTIST
        )
        val alreadyQueuedCandidate = CandidateTrack(
            track = OnlineTrack(videoId = "queued_1", title = "Queued Song", artist = "ArtistSeed"),
            reason = RecommendationReason.SIMILAR_ARTIST
        )
        val freshCandidate = CandidateTrack(
            track = OnlineTrack(videoId = "fresh_1", title = "Fresh Song", artist = "ArtistSeed"),
            reason = RecommendationReason.SIMILAR_ARTIST
        )

        val ranked = ranker.rankCandidates(
            candidates = listOf(exactSeedCandidate, alreadyQueuedCandidate, freshCandidate),
            tasteProfile = tasteProfile,
            targetCount = 3,
            context = RecommendationContext.Radio(
                seedTrack = seed,
                sessionTrackIds = setOf("seed_1", "queued_1")
            )
        )

        val resultIds = ranked.map { it.videoId }.toSet()
        assertFalse("Seed track itself must never be included in generated radio tracks", resultIds.contains("seed_1"))
        assertFalse("Already queued tracks must not be duplicated", resultIds.contains("queued_1"))
        assertTrue("Fresh candidate should be present", resultIds.contains("fresh_1"))
    }

    @Test
    fun testLiveSkipAdaptationInSubsequentBatches() {
        val seed = OnlineTrack(videoId = "seed_1", title = "Seed Song", artist = "ArtistSeed")
        val tasteProfile = TasteProfile(
            artistAffinities = mapOf(
                "artistskipped" to 0.60f,
                "artistalternative" to 0.60f
            )
        )

        val candidateSkippedArtist = CandidateTrack(
            track = OnlineTrack(videoId = "skip_cand_1", title = "Skipped Artist Song", artist = "ArtistSkipped"),
            reason = RecommendationReason.TOP_ARTIST
        )
        val candidateAlternative = CandidateTrack(
            track = OnlineTrack(videoId = "alt_cand_1", title = "Alt Artist Song", artist = "ArtistAlternative"),
            reason = RecommendationReason.TOP_ARTIST
        )

        val session = RadioSession(seedTrack = seed)
        // User skips track from ArtistSkipped during playback
        session.recordSkip(OnlineTrack(videoId = "skipped_track_99", title = "Skipped Track", artist = "ArtistSkipped"))

        val ranked = ranker.rankCandidates(
            candidates = listOf(candidateSkippedArtist, candidateAlternative),
            tasteProfile = tasteProfile,
            targetCount = 2,
            context = RecommendationContext.Radio(
                seedTrack = seed,
                sessionSkippedIds = session.recentlySkippedIds,
                sessionSkippedArtists = session.recentlySkippedArtists
            )
        )

        assertEquals("Alternative artist should rank first over skipped artist", "alt_cand_1", ranked.first().videoId)
        assertTrue(
            "Alternative score must exceed skipped artist score due to live skip penalty",
            ranked[0].score > ranked[1].score
        )
    }

    @Test
    fun testRadioSessionRecordBatchUpdatesCounts() {
        val seed = OnlineTrack(videoId = "seed_1", title = "Seed Song", artist = "ArtistSeed")
        val session = RadioSession(seedTrack = seed)

        assertEquals(0, session.extensionBatchCount)
        assertEquals(0, session.totalGeneratedCount)

        val batch1 = listOf(
            OnlineTrack(videoId = "batch1_1", title = "B1 T1", artist = "ArtistA"),
            OnlineTrack(videoId = "batch1_2", title = "B1 T2", artist = "ArtistB")
        )
        session.recordGeneratedTracks(batch1)

        assertEquals(1, session.extensionBatchCount)
        assertEquals(2, session.totalGeneratedCount)
        assertTrue(session.generatedTrackIds.contains("batch1_1"))
        assertTrue(session.generatedTrackIds.contains("batch1_2"))
    }
}
