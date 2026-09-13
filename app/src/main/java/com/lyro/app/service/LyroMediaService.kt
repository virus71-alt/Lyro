package com.lyro.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lyro.app.MainActivity
import java.io.File

@OptIn(UnstableApi::class)
class LyroMediaService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val cacheDataSourceFactory = getCacheDataSourceFactory(this)
        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LyroMediaService"

        @Volatile
        private var simpleCache: SimpleCache? = null

        @Volatile
        private var activePlaybackHeaders: Map<String, String> = emptyMap()

        fun setPlaybackHeaders(headers: Map<String, String>) {
            activePlaybackHeaders = headers
            Log.d(TAG, "Updated active playback headers: keys=${headers.keys}")
        }

        fun getCache(context: Context): SimpleCache {
            return simpleCache ?: synchronized(this) {
                simpleCache ?: run {
                    val cacheDir = File(context.cacheDir, "lyro_media_cache")
                    val evictor = LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024L) // 100MB
                    val databaseProvider = androidx.media3.database.StandaloneDatabaseProvider(context)
                    SimpleCache(cacheDir, evictor, databaseProvider).also { simpleCache = it }
                }
            }
        }

        fun getCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
            // 1. Configure standard HTTP DataSource with proper timeouts and cross-protocol redirects
            val baseHttpFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)

            // 2. Wrap HTTP DataSource with ResolvingDataSource to apply active profile request headers (e.g. User-Agent)
            val resolvingHttpFactory = ResolvingDataSource.Factory(
                baseHttpFactory,
                object : ResolvingDataSource.Resolver {
                    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                        val headers = activePlaybackHeaders
                        return if (headers.isNotEmpty() && dataSpec.uri.host?.contains("googlevideo") == true) {
                            val mergedHeaders = HashMap(dataSpec.httpRequestHeaders)
                            mergedHeaders.putAll(headers)
                            dataSpec.buildUpon().setHttpRequestHeaders(mergedHeaders).build()
                        } else {
                            dataSpec
                        }
                    }
                }
            )

            // 3. Wrap with DefaultDataSource (handles content://, file:// for local songs, and delegates http:// to resolvingHttpFactory)
            val upstreamFactory = DefaultDataSource.Factory(context, resolvingHttpFactory)

            // 4. Wrap with CacheDataSource (caching chunks, ignoring cache on error)
            return CacheDataSource.Factory()
                .setCache(getCache(context))
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
    }
}
