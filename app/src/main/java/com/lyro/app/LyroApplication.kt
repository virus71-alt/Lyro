package com.lyro.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.lyro.app.core.artwork.AudioArtworkFetcher
import com.lyro.app.data.local.LyroDatabaseHelper
import com.lyro.app.data.repository.MusicRepository
import com.lyro.app.service.PlaybackManager

import coil.disk.DiskCache
import coil.memory.MemoryCache

class LyroApplication : Application(), ImageLoaderFactory {

    lateinit var databaseHelper: LyroDatabaseHelper
        private set

    lateinit var localMediaIndex: com.lyro.app.core.matcher.LocalMediaIndex
        private set

    lateinit var playbackSourceResolver: com.lyro.app.service.PlaybackSourceResolver
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

    lateinit var listeningEventRepository: com.lyro.app.recommendation.data.ListeningEventRepository
        private set

    lateinit var tasteProfileRepository: com.lyro.app.recommendation.data.TasteProfileRepository
        private set

    lateinit var recommendationEngine: com.lyro.app.recommendation.engine.RecommendationEngine
        private set

    lateinit var radioManager: com.lyro.app.recommendation.radio.RadioManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        playerPreferences = com.lyro.app.data.preferences.PlayerPreferences(this)
        databaseHelper = LyroDatabaseHelper(this)
        localMediaIndex = com.lyro.app.core.matcher.LocalMediaIndex()
        playbackSourceResolver = com.lyro.app.service.PlaybackSourceResolver(this, localMediaIndex)
        musicRepository = MusicRepository(this, databaseHelper, localMediaIndex)
        onlineMusicRepository = com.lyro.app.data.repository.OnlineMusicRepository()
        playbackManager = PlaybackManager(this, musicRepository, localMediaIndex = localMediaIndex, playbackSourceResolver = playbackSourceResolver)
        musicDownloader = com.lyro.app.data.download.MusicDownloader(this, musicRepository, localMediaIndex = localMediaIndex)

        // Initialize local-first personalized recommendation brain
        listeningEventRepository = com.lyro.app.recommendation.data.ListeningEventRepository(databaseHelper)
        tasteProfileRepository = com.lyro.app.recommendation.data.TasteProfileRepository(databaseHelper, listeningEventRepository)
        val candidateGenerator = com.lyro.app.recommendation.engine.CandidateGenerator(onlineMusicRepository, listeningEventRepository)
        val recommendationRanker = com.lyro.app.recommendation.engine.RecommendationRanker()
        recommendationEngine = com.lyro.app.recommendation.engine.RecommendationEngine(
            eventRepository = listeningEventRepository,
            tasteProfileRepository = tasteProfileRepository,
            candidateGenerator = candidateGenerator,
            ranker = recommendationRanker
        )

        // Initialize Lyro Radio Manager
        radioManager = com.lyro.app.recommendation.radio.RadioManager(
            playbackManager = playbackManager,
            candidateGenerator = candidateGenerator,
            ranker = recommendationRanker,
            tasteProfileRepository = tasteProfileRepository,
            eventRepository = listeningEventRepository,
            musicRepository = musicRepository
        )
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AudioArtworkFetcher.Factory(this@LyroApplication))
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }

    companion object {
        lateinit var instance: LyroApplication
            private set
    }
}
