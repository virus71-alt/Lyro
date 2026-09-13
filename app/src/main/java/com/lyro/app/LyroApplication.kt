package com.lyro.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.lyro.app.core.artwork.AudioArtworkFetcher
import com.lyro.app.data.local.LyroDatabaseHelper
import com.lyro.app.data.repository.MusicRepository
import com.lyro.app.service.PlaybackManager

class LyroApplication : Application(), ImageLoaderFactory {

    lateinit var databaseHelper: LyroDatabaseHelper
        private set

    lateinit var musicRepository: MusicRepository
        private set

    lateinit var onlineMusicRepository: com.lyro.app.data.repository.OnlineMusicRepository
        private set

    lateinit var playbackManager: PlaybackManager
        private set

    lateinit var playerPreferences: com.lyro.app.data.preferences.PlayerPreferences
        private set

    lateinit var musicDownloader: com.lyro.app.data.download.MusicDownloader
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        playerPreferences = com.lyro.app.data.preferences.PlayerPreferences(this)
        databaseHelper = LyroDatabaseHelper(this)
        musicRepository = MusicRepository(this, databaseHelper)
        onlineMusicRepository = com.lyro.app.data.repository.OnlineMusicRepository()
        playbackManager = PlaybackManager(this, musicRepository)
        musicDownloader = com.lyro.app.data.download.MusicDownloader(this, musicRepository)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AudioArtworkFetcher.Factory(this@LyroApplication))
            }
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: LyroApplication
            private set
    }
}
