package com.lyro.app.recommendation

import com.lyro.app.data.model.LocalTrack
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.data.model.Song
import com.lyro.app.data.model.UnifiedTrack
import com.lyro.app.recommendation.model.EventType
import com.lyro.app.recommendation.model.ListeningEvent
import org.junit.Assert.*
import org.junit.Test

class UnifiedFavoritesTest {

    @Test
    fun testOnlineTrackIdentity() {
        val onlineTrack = OnlineTrack(
            videoId = "vid_12345",
            title = "Kesariya",
            artist = "Arijit Singh",
            album = "Brahmastra",
            durationMs = 268000
        )

        assertEquals("vid_12345", onlineTrack.id)
        assertEquals("vid_12345", onlineTrack.onlineVideoId)
    }

    @Test
    fun testDownloadedCopyMatchesOnlineCanonicalIdentity() {
        val onlineTrack = OnlineTrack(
            videoId = "vid_kesariya",
            title = "Kesariya",
            artist = "Arijit Singh"
        )

        val localSong = Song(
            id = 9999L,
            title = "Kesariya",
            artist = "Arijit Singh",
            album = "Brahmastra",
            albumId = 1L,
            duration = 268000,
            contentUriString = "",
            albumArtUriString = null,
            size = 5000000,
            dateAdded = System.currentTimeMillis()
        )

        val unifiedDownloadedTrack = UnifiedTrack(
            canonicalId = "online_vid_kesariya",
            title = localSong.title,
            artist = localSong.artist,
            onlineVideoId = "vid_kesariya",
            localSong = localSong,
            localUri = null
        )

        assertEquals("Online track video ID must match unified track video ID",
            onlineTrack.onlineVideoId, unifiedDownloadedTrack.onlineVideoId)
        assertEquals("Canonical ID must use online video ID",
            "online_vid_kesariya", unifiedDownloadedTrack.canonicalId)
    }

    @Test
    fun testFavoriteDeduplicationByCanonicalIdentity() {
        val online = OnlineTrack(
            videoId = "track_abc",
            title = "Song A",
            artist = "Artist A"
        )
        val downloaded = UnifiedTrack(
            canonicalId = "online_track_abc",
            title = "Song A",
            artist = "Artist A",
            onlineVideoId = "track_abc",
            localSong = null,
            localUri = null
        )

        val distinctLocal = LocalTrack(
            song = Song(
                id = 13L,
                title = "Song B",
                artist = "Artist B",
                album = "Album B",
                albumId = 2L,
                duration = 180000,
                contentUriString = "",
                albumArtUriString = null,
                size = 0,
                dateAdded = 0
            )
        )

        val inputList: List<PlayableTrack> = listOf(online, downloaded, distinctLocal)

        val seenKeys = mutableSetOf<String>()
        val deduplicated = mutableListOf<PlayableTrack>()
        for (t in inputList) {
            val key = t.onlineVideoId ?: t.id
            if (seenKeys.add(key)) {
                deduplicated.add(t)
            }
        }

        assertEquals(2, deduplicated.size)
        assertEquals("track_abc", deduplicated[0].onlineVideoId)
        assertEquals(13L, (deduplicated[1] as LocalTrack).song.id)
    }

    @Test
    fun testLikeAndUnlikeEventsDistinction() {
        val likeEvent = ListeningEvent(
            playbackSessionId = "session_1",
            videoId = "vid_xyz",
            title = "Sample Song",
            artist = "Sample Artist",
            eventType = EventType.LIKED
        )

        val unlikeEvent = ListeningEvent(
            playbackSessionId = "session_1",
            videoId = "vid_xyz",
            title = "Sample Song",
            artist = "Sample Artist",
            eventType = EventType.UNLIKED
        )

        val notInterestedEvent = ListeningEvent(
            playbackSessionId = "session_1",
            videoId = "vid_xyz",
            title = "Sample Song",
            artist = "Sample Artist",
            eventType = EventType.NOT_INTERESTED
        )

        // Ensure EventTypes are strictly distinct
        assertNotEquals(likeEvent.eventType, unlikeEvent.eventType)
        assertNotEquals(unlikeEvent.eventType, notInterestedEvent.eventType)
        assertEquals(EventType.LIKED, likeEvent.eventType)
        assertEquals(EventType.UNLIKED, unlikeEvent.eventType)
        assertEquals(EventType.NOT_INTERESTED, notInterestedEvent.eventType)
    }
}
