package com.lyro.app.core.network

import com.lyro.app.core.matcher.LocalMediaIndex
import com.lyro.app.core.offline.OfflineAvailabilityResolver
import com.lyro.app.data.model.LocalTrack
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.Song
import com.lyro.app.service.PlaybackSource
import com.lyro.app.service.PlaybackSourceResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class FakeNetworkMonitor(initialStatus: NetworkStatus = NetworkStatus.ONLINE) : NetworkMonitor {
    private val _status = MutableStateFlow(initialStatus)
    override val networkStatus: StateFlow<NetworkStatus> = _status.asStateFlow()
    private val _isOnline = MutableStateFlow(initialStatus == NetworkStatus.ONLINE)
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    fun setStatus(status: NetworkStatus) {
        _status.value = status
        _isOnline.value = (status == NetworkStatus.ONLINE)
    }
}

class PlaybackSourceResolverOfflineTest {

    private lateinit var fakeNetworkMonitor: FakeNetworkMonitor
    private lateinit var localMediaIndex: LocalMediaIndex
    private lateinit var offlineResolver: OfflineAvailabilityResolver
    private lateinit var sourceResolver: PlaybackSourceResolver
    private var tempFile: File? = null

    @Before
    fun setUp() {
        fakeNetworkMonitor = FakeNetworkMonitor(NetworkStatus.ONLINE)
        localMediaIndex = LocalMediaIndex()
        offlineResolver = OfflineAvailabilityResolver(
            context = null,
            localMediaIndex = localMediaIndex,
            musicDownloader = null,
            dbHelper = null
        )
        sourceResolver = PlaybackSourceResolver(
            context = null,
            localMediaIndex = localMediaIndex,
            networkMonitor = fakeNetworkMonitor,
            offlineAvailabilityResolver = offlineResolver
        )
    }

    @Test
    fun testOnlineOnlyTrackResolvesToOnlineWhenNetworkIsOnline() {
        runBlocking {
            fakeNetworkMonitor.setStatus(NetworkStatus.ONLINE)
            val onlineTrack = OnlineTrack(
                videoId = "vid_stream_test",
                title = "Stream Test",
                artist = "Artist"
            )

            val source = sourceResolver.resolve(onlineTrack)
            assertTrue("When network is online, online track should resolve to Online stream",
                source is PlaybackSource.Online)
            assertEquals("vid_stream_test", (source as PlaybackSource.Online).videoId)
        }
    }

    @Test
    fun testOnlineOnlyTrackResolvesToUnavailableWhenNetworkIsOffline() {
        runBlocking {
            fakeNetworkMonitor.setStatus(NetworkStatus.OFFLINE)
            val onlineTrack = OnlineTrack(
                videoId = "vid_stream_test",
                title = "Stream Test",
                artist = "Artist"
            )

            val source = sourceResolver.resolve(onlineTrack)
            assertTrue("When network is offline, online-only track must resolve to Unavailable",
                source is PlaybackSource.Unavailable)
        }
    }

    @Test
    fun testLocalTrackResolvesToLocalRegardlessOfNetworkStatus() {
        runBlocking {
            val f = File.createTempFile("lyro_local_track", ".mp3")
            f.writeBytes(ByteArray(256) { 0x33.toByte() })
            tempFile = f

            val song = Song(
                id = 501L,
                title = "Local Song",
                artist = "Artist",
                album = "Album",
                albumId = 1L,
                duration = 150000,
                contentUriString = f.absolutePath,
                albumArtUriString = null,
                size = f.length(),
                dateAdded = System.currentTimeMillis()
            )
            val localTrack = LocalTrack(song)

            // Offline check
            fakeNetworkMonitor.setStatus(NetworkStatus.OFFLINE)
            val offlineSource = sourceResolver.resolve(localTrack)
            assertTrue("Local track must resolve to Local even when network is offline",
                offlineSource is PlaybackSource.Local)

            // Online check
            fakeNetworkMonitor.setStatus(NetworkStatus.ONLINE)
            val onlineSource = sourceResolver.resolve(localTrack)
            assertTrue("Local track must resolve to Local when network is online",
                onlineSource is PlaybackSource.Local)

            f.delete()
        }
    }
}
