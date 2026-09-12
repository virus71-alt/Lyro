package com.lyro.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
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

        activeAudioSessionId = player?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET

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
        activeAudioSessionId = C.AUDIO_SESSION_ID_UNSET
        mediaSession?.run {
            player?.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }

    companion object {
        var activeAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
            private set

        @Volatile
        private var simpleCache: SimpleCache? = null

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
            val upstreamFactory = DefaultDataSource.Factory(context)
            return CacheDataSource.Factory()
                .setCache(getCache(context))
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
    }
}
