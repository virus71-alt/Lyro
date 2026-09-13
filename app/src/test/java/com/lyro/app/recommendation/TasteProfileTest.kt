package com.lyro.app.recommendation

import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.Song
import com.lyro.app.recommendation.data.ListeningEventRepository
import com.lyro.app.recommendation.data.TasteProfileRepository
import com.lyro.app.recommendation.model.EventType
import com.lyro.app.recommendation.model.ListeningEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TasteProfileTest {

    private lateinit var eventRepository: ListeningEventRepository
    private lateinit var profileRepository: TasteProfileRepository

    @Before
    fun setUp() {
        eventRepository = ListeningEventRepository(dbHelper = null)
        profileRepository = TasteProfileRepository(dbHelper = null, eventRepository = eventRepository)
    }

    @Test
    fun testPositiveAffinityGrowthOnCompletions() = runBlocking {
        val now = System.currentTimeMillis()
        // Simulate user completing 3 songs from ArtistAlpha
        repeat(3) { i ->
            eventRepository.recordEvent(
                ListeningEvent(
                    playbackSessionId = "session_$i",
                    videoId = "vid_alpha_$i",
                    title = "Alpha Track $i",
                    artist = "ArtistAlpha",
                    eventType = EventType.PLAY_COMPLETED,
                    timestamp = now
                )
            )
        }

        val profile = profileRepository.computeProfile(referenceTime = now)
        val alphaScore = profile.getArtistAffinity("ArtistAlpha")

        assertTrue("Expected ArtistAlpha affinity to be positive", alphaScore > 0f)
        assertTrue("Expected ArtistAlpha affinity to be normalized <= 1.0", alphaScore <= 1.0f)
        assertEquals("ArtistAlpha should be top artist", "artistalpha", profile.topArtists.first().first)
    }

    @Test
    fun testSingleSkipDoesNotPermanentlyDestroyAffinity() = runBlocking {
        val now = System.currentTimeMillis()
        // User listens to and completes 2 tracks from ArtistBeta (+6 * 2 = +12)
        repeat(2) { i ->
            eventRepository.recordEvent(
                ListeningEvent(
                    playbackSessionId = "session_beta_$i",
                    videoId = "vid_beta_$i",
                    title = "Beta Song $i",
                    artist = "ArtistBeta",
                    eventType = EventType.PLAY_COMPLETED,
                    timestamp = now
                )
            )
        }

        // Accidental early skip (-4)
        eventRepository.recordEvent(
            ListeningEvent(
                playbackSessionId = "session_beta_skip",
                videoId = "vid_beta_skipped",
                title = "Beta Accidental Skip",
                artist = "ArtistBeta",
                eventType = EventType.SKIPPED_EARLY,
                timestamp = now
            )
        )

        val profile = profileRepository.computeProfile(referenceTime = now)
        val betaScore = profile.getArtistAffinity("ArtistBeta")

        // 12 - 4 = 8 positive points remaining. Affinity must remain strong and not destroyed!
        assertTrue("Single skip must not eliminate artist affinity", betaScore > 0.20f)
        assertFalse("Artist should not have 0 affinity after single skip", betaScore == 0f)
    }

    @Test
    fun testRepeatedSkipsAggressivelyPenalize() = runBlocking {
        val now = System.currentTimeMillis()
        // User repeatedly skips ArtistGamma 4 times (-4 * 4 = -16)
        repeat(4) { i ->
            eventRepository.recordEvent(
                ListeningEvent(
                    playbackSessionId = "session_gamma_$i",
                    videoId = "vid_gamma_$i",
                    title = "Gamma Track $i",
                    artist = "ArtistGamma",
                    eventType = EventType.SKIPPED_EARLY,
                    timestamp = now
                )
            )
        }

        val profile = profileRepository.computeProfile(referenceTime = now)
        val gammaScore = profile.getArtistAffinity("ArtistGamma")

        assertEquals("Aggregated skips should floor affinity to 0", 0.0f, gammaScore, 0.001f)
        assertTrue("ArtistGamma should be recorded in recent skips", profile.isArtistRecentlySkipped("ArtistGamma"))
    }

    @Test
    fun testTimeDecayReducesOldPlayInfluence() = runBlocking {
        val now = System.currentTimeMillis()
        val fortyTwoDaysAgo = now - (42L * 24 * 60 * 60 * 1000) // 2 half-lives

        // Old play from 42 days ago
        eventRepository.recordEvent(
            ListeningEvent(
                playbackSessionId = "session_old",
                videoId = "vid_old",
                title = "Old Track",
                artist = "ArtistOldHistory",
                eventType = EventType.PLAY_COMPLETED,
                timestamp = fortyTwoDaysAgo
            )
        )

        // Recent play from today
        eventRepository.recordEvent(
            ListeningEvent(
                playbackSessionId = "session_recent",
                videoId = "vid_recent",
                title = "Recent Track",
                artist = "ArtistRecentHistory",
                eventType = EventType.PLAY_COMPLETED,
                timestamp = now
            )
        )

        val profile = profileRepository.computeProfile(referenceTime = now)
        val oldScore = profile.getArtistAffinity("ArtistOldHistory")
        val recentScore = profile.getArtistAffinity("ArtistRecentHistory")

        assertTrue("Recent play should have higher affinity than 42-day decayed play", recentScore > oldScore)
        assertTrue("Old score should reflect decay (less than half of recent)", oldScore < recentScore * 0.5f)
    }

    private fun createMockSong(
        id: Long,
        title: String,
        artist: String,
        album: String = "Local Album",
        isFavorite: Boolean = false,
        dateAdded: Long = System.currentTimeMillis() / 1000
    ): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = 1L,
        duration = 180000L,
        contentUriString = "content://media/external/audio/media/$id",
        albumArtUriString = null,
        size = 5000000L,
        dateAdded = dateAdded,
        isFavorite = isFavorite
    )

    @Test
    fun testColdStartLocalLibraryBootstrapping() = runBlocking {
        val now = System.currentTimeMillis()
        // User has 5 local songs on device by ArtistLocal
        val localSongs = (1..5).map { id ->
            createMockSong(
                id = id.toLong(),
                title = "Local Song $id",
                artist = "ArtistLocal",
                dateAdded = now / 1000
            )
        }

        val profile = profileRepository.computeProfile(localSongs = localSongs, referenceTime = now)
        val localArtistScore = profile.getArtistAffinity("ArtistLocal")

        assertTrue("Local library songs should bootstrap cold-start affinity", localArtistScore > 0f)
        assertEquals("artistlocal", profile.topArtists.first().first)
    }

    @Test
    fun testColdStartFavoriteAndDownloadBoost() = runBlocking {
        val now = System.currentTimeMillis()
        val favSong = createMockSong(
            id = 100L,
            title = "Fav Song",
            artist = "ArtistFavorite",
            isFavorite = true,
            dateAdded = now / 1000
        )
        val downloadedTrack = OnlineTrack(
            videoId = "dl_123",
            title = "DL Song",
            artist = "ArtistDownload",
            album = "DL Album"
        )

        val profile = profileRepository.computeProfile(
            localSongs = listOf(favSong),
            downloadedTracks = listOf(downloadedTrack),
            referenceTime = now
        )

        val favScore = profile.getArtistAffinity("ArtistFavorite")
        val dlScore = profile.getArtistAffinity("ArtistDownload")

        assertTrue("Favorite track must boost artist affinity strongly", favScore > 0.20f)
        assertTrue("Downloaded track must boost artist affinity strongly", dlScore > 0.15f)
    }
}
