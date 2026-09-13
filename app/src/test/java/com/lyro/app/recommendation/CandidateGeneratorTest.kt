package com.lyro.app.recommendation

import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.repository.OnlineMusicRepository
import com.lyro.app.recommendation.data.ListeningEventRepository
import com.lyro.app.recommendation.engine.CandidateGenerator
import com.lyro.app.recommendation.model.TasteProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CandidateGeneratorTest {

    private lateinit var eventRepository: ListeningEventRepository

    @Before
    fun setUp() {
        eventRepository = ListeningEventRepository(dbHelper = null)
    }

    // Mock OnlineMusicRepository that records queries and returns mock tracks
    private class MockOnlineMusicRepository : OnlineMusicRepository() {
        val capturedQueries = mutableListOf<String>()

        override suspend fun searchSongs(query: String): Result<List<OnlineTrack>> {
            capturedQueries.add(query)
            val mockTrack = OnlineTrack(
                videoId = "vid_${query.hashCode()}",
                title = "Track for $query",
                artist = query.substringBefore(" ")
            )
            return Result.success(listOf(mockTrack))
        }
    }

    @Test
    fun testQueriesGeneratedDynamicallyFromUserTasteProfileWithoutHardcoding() = runBlocking {
        val mockOnline = MockOnlineMusicRepository()
        val generator = CandidateGenerator(mockOnline, eventRepository)

        val tasteProfile = TasteProfile(
            artistAffinities = mapOf("artistzebra" to 0.85f, "artistgiraffe" to 0.70f),
            languageAffinities = mapOf("langkrypton" to 0.75f),
            genreAffinities = mapOf("genrenebula" to 0.65f)
        )

        val candidates = generator.generateCandidatePool(tasteProfile, targetPoolSize = 20)

        // Verify captured queries match the user's dynamic taste profile
        val queries = mockOnline.capturedQueries
        assertTrue("Queries must contain user's top artist", queries.any { it.contains("artistzebra", ignoreCase = true) })
        assertTrue("Queries must contain adjacent discovery for top artist", queries.any { it.contains("artistzebra similar songs", ignoreCase = true) })
        assertTrue("Queries must contain language match", queries.any { it.contains("langkrypton songs", ignoreCase = true) })
        assertTrue("Queries must contain genre match", queries.any { it.contains("genrenebula hits", ignoreCase = true) })

        // Verify no hardcoded artists like "Arijit" or "Pritam" are queried
        assertFalse("Must never query hardcoded Arijit", queries.any { it.contains("Arijit", ignoreCase = true) })
        assertFalse("Must never query hardcoded Pritam", queries.any { it.contains("Pritam", ignoreCase = true) })
        assertFalse("Must never query hardcoded Aujla", queries.any { it.contains("Aujla", ignoreCase = true) })
        assertFalse("Must never query hardcoded Nepali", queries.any { it.contains("Nepali", ignoreCase = true) })

        assertTrue("Candidate pool should be populated", candidates.isNotEmpty())
    }

    @Test
    fun testNotInterestedTracksAreExcludedFromCandidatePool() = runBlocking {
        val mockOnline = object : OnlineMusicRepository() {
            override suspend fun searchSongs(query: String): Result<List<OnlineTrack>> {
                return Result.success(
                    listOf(
                        OnlineTrack(videoId = "blocked_vid", title = "Blocked Track", artist = "ArtistBlocked"),
                        OnlineTrack(videoId = "allowed_vid", title = "Allowed Track", artist = "ArtistAllowed")
                    )
                )
            }
        }

        val generator = CandidateGenerator(mockOnline, eventRepository)

        // Mark blocked_vid as not interested
        eventRepository.markNotInterested("blocked_vid", "Blocked Track", "ArtistBlocked")

        val tasteProfile = TasteProfile(
            artistAffinities = mapOf("artistallowed" to 0.80f)
        )

        val candidates = generator.generateCandidatePool(tasteProfile, targetPoolSize = 10)

        assertFalse("Blocked track must not appear in candidate pool", candidates.any { it.videoId == "blocked_vid" })
        assertTrue("Allowed track must appear in candidate pool", candidates.any { it.videoId == "allowed_vid" })
    }

    @Test
    fun testColdStartQueriesNeutralExplorationSeedsWhenProfileIsEmpty() = runBlocking {
        val mockOnline = MockOnlineMusicRepository()
        val generator = CandidateGenerator(mockOnline, eventRepository)

        val emptyProfile = TasteProfile()
        val candidates = generator.generateCandidatePool(emptyProfile, targetPoolSize = 10)

        assertTrue("Captured queries should contain neutral seeds", mockOnline.capturedQueries.isNotEmpty())
        assertTrue("Candidate pool should not be empty", candidates.isNotEmpty())
    }
}
