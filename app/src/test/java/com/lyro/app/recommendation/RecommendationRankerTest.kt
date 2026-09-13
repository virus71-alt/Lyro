package com.lyro.app.recommendation

import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.recommendation.engine.RecommendationRanker
import com.lyro.app.recommendation.model.CandidateTrack
import com.lyro.app.recommendation.model.RecommendationReason
import com.lyro.app.recommendation.model.TasteProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RecommendationRankerTest {

    private lateinit var ranker: RecommendationRanker

    @Before
    fun setUp() {
        ranker = RecommendationRanker()
    }

    @Test
    fun testRankingFavorsHighAffinityArtist() {
        val tasteProfile = TasteProfile(
            artistAffinities = mapOf("artistloved" to 0.90f, "artistneutral" to 0.10f)
        )

        val trackLoved = OnlineTrack(videoId = "vid_1", title = "Loved Song", artist = "ArtistLoved")
        val trackNeutral = OnlineTrack(videoId = "vid_2", title = "Neutral Song", artist = "ArtistNeutral")

        val candLoved = CandidateTrack(trackLoved, RecommendationReason.TOP_ARTIST)
        val candNeutral = CandidateTrack(trackNeutral, RecommendationReason.TOP_ARTIST)

        val ranked = ranker.rankCandidates(
            candidates = listOf(candNeutral, candLoved),
            tasteProfile = tasteProfile,
            targetCount = 2
        )

        assertEquals("Track from high affinity artist must rank first", "vid_1", ranked.first().videoId)
        assertTrue("Loved track score must exceed neutral track", ranked[0].score > ranked[1].score)
    }

    @Test
    fun testArtistDiversityLimitEnforced() {
        val tasteProfile = TasteProfile(
            artistAffinities = mapOf(
                "artistdominant" to 0.95f,
                "artistother" to 0.50f,
                "artistthird" to 0.40f
            )
        )

        // 8 tracks from dominant artist
        val dominantCandidates = (1..8).map { i ->
            CandidateTrack(
                track = OnlineTrack(videoId = "dom_$i", title = "Dom Song $i", artist = "ArtistDominant"),
                reason = RecommendationReason.TOP_ARTIST
            )
        }
        // 5 tracks from other artist
        val otherCandidates = (1..5).map { i ->
            CandidateTrack(
                track = OnlineTrack(videoId = "oth_$i", title = "Other Song $i", artist = "ArtistOther"),
                reason = RecommendationReason.TOP_ARTIST
            )
        }
        // 5 tracks from third artist
        val thirdCandidates = (1..5).map { i ->
            CandidateTrack(
                track = OnlineTrack(videoId = "thd_$i", title = "Third Song $i", artist = "ArtistThird"),
                reason = RecommendationReason.TOP_ARTIST
            )
        }

        val allCandidates = dominantCandidates + otherCandidates + thirdCandidates

        val ranked = ranker.rankCandidates(
            candidates = allCandidates,
            tasteProfile = tasteProfile,
            targetCount = 8
        )

        val dominantInBatch = ranked.count { it.artist.equals("ArtistDominant", ignoreCase = true) }
        assertEquals("Target count of 8 should be fulfilled", 8, ranked.size)
        assertTrue(
            "Batch must not contain more than MAX_TRACKS_PER_ARTIST (${RecommendationConfig.MAX_TRACKS_PER_ARTIST}) by same artist, found $dominantInBatch",
            dominantInBatch <= RecommendationConfig.MAX_TRACKS_PER_ARTIST
        )
    }

    @Test
    fun testRecentlyShownPenaltyReducesScore() {
        val tasteProfile = TasteProfile(
            artistAffinities = mapOf("artistsame" to 0.80f)
        )

        val freshTrack = OnlineTrack(videoId = "fresh_vid", title = "Fresh Song", artist = "ArtistSame")
        val repeatedTrack = OnlineTrack(videoId = "seen_vid", title = "Repeated Song", artist = "ArtistSame")

        val candFresh = CandidateTrack(freshTrack, RecommendationReason.TOP_ARTIST)
        val candRepeated = CandidateTrack(repeatedTrack, RecommendationReason.TOP_ARTIST)

        val scoreFresh = ranker.calculateScore(
            candidate = candFresh,
            tasteProfile = tasteProfile,
            recentlyShownIds = setOf("seen_vid"),
            overplayedIds = emptySet()
        )

        val scoreRepeated = ranker.calculateScore(
            candidate = candRepeated,
            tasteProfile = tasteProfile,
            recentlyShownIds = setOf("seen_vid"),
            overplayedIds = emptySet()
        )

        assertTrue(
            "Candidate in recently shown history must receive score penalty",
            scoreFresh > scoreRepeated
        )
    }

    @Test
    fun testRecentlySkippedPenaltyApplied() {
        val tasteProfile = TasteProfile(
            artistAffinities = mapOf("artistskipped" to 0.50f),
            recentSkippedArtists = setOf("artistskipped")
        )

        val skippedArtistTrack = OnlineTrack(videoId = "skip_vid", title = "Skipped Song", artist = "ArtistSkipped")
        val cand = CandidateTrack(skippedArtistTrack, RecommendationReason.TOP_ARTIST)

        val score = ranker.calculateScore(
            candidate = cand,
            tasteProfile = tasteProfile,
            recentlyShownIds = emptySet(),
            overplayedIds = emptySet()
        )

        val normalTasteProfile = TasteProfile(
            artistAffinities = mapOf("artistskipped" to 0.50f),
            recentSkippedArtists = emptySet()
        )
        val normalScore = ranker.calculateScore(
            candidate = cand,
            tasteProfile = normalTasteProfile,
            recentlyShownIds = emptySet(),
            overplayedIds = emptySet()
        )

        assertTrue("Candidate from recently skipped artist must receive skip penalty", score < normalScore)
    }

    @Test
    fun testExplorationMixMaintainsDiversityOfSourceTypes() {
        val tasteProfile = TasteProfile(
            artistAffinities = mapOf("artistfamiliar" to 0.85f)
        )

        val familiarCandidates = (1..6).map { i ->
            CandidateTrack(
                track = OnlineTrack(videoId = "fam_$i", title = "Fam $i", artist = "ArtistFamiliar"),
                reason = RecommendationReason.TOP_ARTIST
            )
        }
        val adjacentCandidates = (1..4).map { i ->
            CandidateTrack(
                track = OnlineTrack(videoId = "adj_$i", title = "Adj $i", artist = "ArtistAdjacent$i"),
                reason = RecommendationReason.SIMILAR_ARTIST
            )
        }
        val explorationCandidates = (1..4).map { i ->
            CandidateTrack(
                track = OnlineTrack(videoId = "exp_$i", title = "Exp $i", artist = "ArtistExplore$i"),
                reason = RecommendationReason.EXPLORATION
            )
        }

        val allCandidates = familiarCandidates + adjacentCandidates + explorationCandidates

        val ranked = ranker.rankCandidates(
            candidates = allCandidates,
            tasteProfile = tasteProfile,
            targetCount = 10,
            familiarRatio = 0.70f,
            adjacentRatio = 0.20f,
            explorationRatio = 0.10f
        )

        val hasFamiliar = ranked.any { it.isFamiliar }
        val hasAdjacent = ranked.any { it.isAdjacent }
        val hasExploration = ranked.any { it.isExploration }

        assertTrue("Ranked batch should contain familiar tracks", hasFamiliar)
        assertTrue("Ranked batch should contain adjacent discovery tracks", hasAdjacent)
        assertTrue("Ranked batch should contain exploration tracks", hasExploration)
    }
}
