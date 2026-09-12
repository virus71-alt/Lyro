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

    lateinit var playbackManager: PlaybackManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        databaseHelper = LyroDatabaseHelper(this)
        musicRepository = MusicRepository(this, databaseHelper)
        playbackManager = PlaybackManager(this, musicRepository)
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
