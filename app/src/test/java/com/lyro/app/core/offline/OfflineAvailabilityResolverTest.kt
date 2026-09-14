package com.lyro.app.core.offline

import com.lyro.app.core.matcher.LocalMediaIndex
import com.lyro.app.data.local.DownloadedMetadata
import com.lyro.app.data.model.LocalTrack
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.Song
import com.lyro.app.data.model.UnifiedTrack
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class OfflineAvailabilityResolverTest {

    private lateinit var localMediaIndex: LocalMediaIndex
    private lateinit var resolver: OfflineAvailabilityResolver
    private val createdFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        localMediaIndex = LocalMediaIndex()
        resolver = OfflineAvailabilityResolver(
            context = null,
            localMediaIndex = localMediaIndex,
            musicDownloader = null,
            dbHelper = null
        )
    }

    @After
    fun tearDown() {
        for (f in createdFiles) {
            try {
                if (f.exists()) f.delete()
            } catch (_: Exception) {}
        }
        createdFiles.clear()
    }

    private fun createTempAudioFile(name: String): File {
        val file = File.createTempFile("lyro_test_$name", ".mp3")
        file.writeBytes(ByteArray(1024) { 0x42.toByte() })
        createdFiles.add(file)
        return file
    }

    @Test
    fun testLocalTrackWithExistingFileIsAvailableOffline() {
        val tempFile = createTempAudioFile("song_a")
        val song = Song(
            id = 101L,
            title = "Track A",
            artist = "Artist A",
            album = "Album A",
            albumId = 1L,
            duration = 180000,
            contentUriString = tempFile.absolutePath,
            albumArtUriString = null,
            size = tempFile.length(),
            dateAdded = System.currentTimeMillis()
        )
        val localTrack = LocalTrack(song)

        val isAvailable = resolver.isAvailableOffline(localTrack)
        assertTrue("Track with existing, readable file should be available offline", isAvailable)
    }

    @Test
    fun testLocalTrackWithMissingFileIsNotAvailableOffline() {
        val nonExistentPath = File(System.getProperty("java.io.tmpdir"), "non_existent_lyro_track.mp3").absolutePath
        val song = Song(
            id = 102L,
            title = "Missing Track",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            duration = 180000,
            contentUriString = nonExistentPath,
            albumArtUriString = null,
            size = 0L,
            dateAdded = System.currentTimeMillis()
        )
        val localTrack = LocalTrack(song)

        val isAvailable = resolver.isAvailableOffline(localTrack)
        assertFalse("Track with missing file must not be available offline", isAvailable)
    }

    @Test
    fun testOnlineTrackWithoutDownloadIsNotAvailableOffline() {
        val onlineTrack = OnlineTrack(
            videoId = "vid_online_only",
            title = "Streaming Track",
            artist = "Online Artist"
        )

        val isAvailable = resolver.isAvailableOffline(onlineTrack)
        assertFalse("Online-only track without download must not be available offline", isAvailable)
    }

    @Test
    fun testOnlineTrackWithValidDownloadedFileIsAvailableOffline() {
        val tempFile = createTempAudioFile("vid_downloaded_1")
        val videoId = "vid_downloaded_1"

        // Register download in LocalMediaIndex
        val meta = DownloadedMetadata(
            videoId = videoId,
            displayName = "Downloaded Hit",
            title = "Downloaded Hit",
            artist = "Downloaded Artist",
            album = "Single",
            thumbnailUri = null,
            durationMs = 210000,
            localUri = tempFile.absolutePath
        )
        localMediaIndex.rebuild(emptyList(), listOf(meta))

        val onlineTrack = OnlineTrack(
            videoId = videoId,
            title = "Downloaded Hit",
            artist = "Downloaded Artist"
        )

        val isAvailable = resolver.isAvailableOffline(onlineTrack)
        assertTrue("Online track with verified local download file must be available offline", isAvailable)
    }

    @Test
    fun testStaleDownloadMappingIsInvalidatedWhenFileDeleted() {
        val tempFile = createTempAudioFile("vid_stale_1")
        val videoId = "vid_stale_1"
        val path = tempFile.absolutePath

        val meta = DownloadedMetadata(
            videoId = videoId,
            displayName = "Deleted Song",
            title = "Deleted Song",
            artist = "Ghost Artist",
            album = "Album",
            thumbnailUri = null,
            durationMs = 200000,
            localUri = path
        )
        localMediaIndex.rebuild(emptyList(), listOf(meta))

        // Now externally delete the file
        tempFile.delete()
        assertFalse(tempFile.exists())

        val onlineTrack = OnlineTrack(
            videoId = videoId,
            title = "Deleted Song",
            artist = "Ghost Artist"
        )

        val isAvailable = resolver.isAvailableOffline(onlineTrack)
        assertFalse("Stale download where file was deleted must NOT be available offline", isAvailable)

        // Verify that LocalMediaIndex mapping was invalidated
        assertNull("LocalMediaIndex mapping must be cleared for deleted download",
            localMediaIndex.getLocalUriForVideoId(videoId))
    }

    @Test
    fun testUnifiedTrackWithValidLocalSongIsAvailableOffline() {
        val tempFile = createTempAudioFile("unified_track")
        val song = Song(
            id = 201L,
            title = "Unified Title",
            artist = "Unified Artist",
            album = "Album",
            albumId = 1L,
            duration = 240000,
            contentUriString = tempFile.absolutePath,
            albumArtUriString = null,
            size = tempFile.length(),
            dateAdded = System.currentTimeMillis()
        )

        val unifiedTrack = UnifiedTrack(
            canonicalId = "unified_test_1",
            title = song.title,
            artist = song.artist,
            onlineVideoId = "vid_xyz",
            localSong = song,
            localUri = null
        )

        val isAvailable = resolver.isAvailableOffline(unifiedTrack)
        assertTrue("Unified track with valid local song must be available offline", isAvailable)
    }
}
