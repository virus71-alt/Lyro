package com.lyro.app.recommendation.data

import android.util.Log
import com.lyro.app.data.local.LyroDatabaseHelper
import com.lyro.app.recommendation.RecommendationConfig
import com.lyro.app.recommendation.model.ListeningEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Repository responsible for local on-device persistence and retrieval of listening events,
 * recommendation history, and user negative signals ("Not interested").
 */
open class ListeningEventRepository(
    private val dbHelper: LyroDatabaseHelper? = null
) {
    companion object {
        private const val TAG = "LyroRecommendation"
    }

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val inMemoryEvents = mutableListOf<ListeningEvent>()

    // Emits new listening events so observers (like TasteProfileRepository) can react if desired
    private val _eventStream = MutableSharedFlow<ListeningEvent>(extraBufferCapacity = 64)
    val eventStream = _eventStream.asSharedFlow()

    // In-memory cache of not-interested video IDs for fast candidate filtering
    @Volatile
    private var notInterestedCache: Set<String> = emptySet()

    init {
        if (dbHelper != null) {
            repositoryScope.launch {
                loadNotInterestedCache()
            }
        }
    }

    private suspend fun loadNotInterestedCache() = withContext(Dispatchers.IO) {
        try {
            dbHelper?.let { notInterestedCache = it.getAllNotInterestedVideoIds() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to preload not-interested cache: ${e.message}")
        }
    }

    /**
     * Records a single listening milestone or user interaction.
     * Persisted strictly on-device in SQLite when database helper is available.
     */
    fun recordEvent(event: ListeningEvent) {
        synchronized(inMemoryEvents) {
            inMemoryEvents.add(0, event)
        }
        if (dbHelper != null) {
            repositoryScope.launch {
                try {
                    dbHelper.insertListeningEvent(event)
                    _eventStream.tryEmit(event)
                    Log.d(
                        TAG,
                        "Recorded event: type=${event.eventType}, artist='${event.artist}', title='${event.title}', weight=${event.eventType.baseWeight()}"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to insert listening event: ${e.message}", e)
                }
            }
        } else {
            _eventStream.tryEmit(event)
        }
    }

    /**
     * Retrieves recent listening events for taste profile calculation.
     */
    suspend fun getRecentEvents(
        limit: Int = RecommendationConfig.MAX_PERSISTED_EVENTS
    ): List<ListeningEvent> = withContext(Dispatchers.IO) {
        if (dbHelper != null) {
            dbHelper.getRecentListeningEvents(limit)
        } else {
            synchronized(inMemoryEvents) {
                inMemoryEvents.take(limit).toList()
            }
        }
    }

    /**
     * Prunes old events beyond configured maximum age or count to keep database compact.
     */
    suspend fun pruneOldEvents() = withContext(Dispatchers.IO) {
        try {
            val maxAgeMs = RecommendationConfig.MAX_EVENT_AGE_DAYS * 24L * 60L * 60L * 1000L
            dbHelper?.pruneOldListeningEvents(maxAgeMs, RecommendationConfig.MAX_PERSISTED_EVENTS)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to prune old listening events: ${e.message}")
        }
    }

    private val inMemoryRecentHistory = LinkedHashSet<String>()

    /**
     * Marks a track as "Not interested".
     * Persisted in database and in-memory cache, and records a high negative signal event.
     */
    fun markNotInterested(videoId: String, title: String, artist: String) {
        notInterestedCache = notInterestedCache + videoId
        repositoryScope.launch {
            try {
                dbHelper?.markNotInterested(videoId, title, artist)

                // Record explicit negative listening event
                val negativeEvent = ListeningEvent(
                    playbackSessionId = "explicit_negative",
                    videoId = videoId,
                    title = title,
                    artist = artist,
                    eventType = com.lyro.app.recommendation.model.EventType.NOT_INTERESTED,
                    timestamp = System.currentTimeMillis()
                )
                dbHelper?.insertListeningEvent(negativeEvent)
                _eventStream.tryEmit(negativeEvent)
                Log.d(TAG, "Marked track as Not Interested: videoId=$videoId, artist='$artist'")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark not interested: ${e.message}", e)
            }
        }
    }

    fun isNotInterested(videoId: String): Boolean {
        return notInterestedCache.contains(videoId)
    }

    fun getNotInterestedVideoIds(): Set<String> {
        return notInterestedCache
    }

    suspend fun recordRecentlyRecommended(
        videoIds: Collection<String>,
        section: String
    ) = withContext(Dispatchers.IO) {
        try {
            inMemoryRecentHistory.addAll(videoIds)
            dbHelper?.recordRecommendationHistory(videoIds, section)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record recommendation history: ${e.message}")
        }
    }

    suspend fun getRecentlyRecommendedVideoIds(
        limit: Int = RecommendationConfig.MAX_RECENT_SHOWN_HISTORY
    ): Set<String> = withContext(Dispatchers.IO) {
        try {
            if (dbHelper != null) {
                dbHelper.getRecentRecommendationHistory(limit)
            } else {
                inMemoryRecentHistory.toList().takeLast(limit).toSet()
            }
        } catch (e: Exception) {
            emptySet()
        }
    }
}
