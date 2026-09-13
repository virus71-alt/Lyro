package com.lyro.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import com.lyro.app.data.model.LocalTrack
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.data.model.Playlist
import com.lyro.app.data.model.UnifiedTrack
import com.lyro.app.recommendation.model.EventType
import com.lyro.app.recommendation.model.ListeningEvent

class LyroDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "lyro_music.db"
        private const val DATABASE_VERSION = 6

        // Tables
        const val TABLE_FAVORITES = "favorites"
        const val TABLE_UNIFIED_FAVORITES = "unified_favorites"
        const val TABLE_PLAYLISTS = "playlists"
        const val TABLE_PLAYLIST_SONGS = "playlist_songs"
        const val TABLE_HISTORY = "history"
        const val TABLE_DOWNLOADED_METADATA = "downloaded_metadata"
        const val TABLE_HOME_FEED_CACHE = "home_feed_cache"

        // Recommendation Engine Tables
        const val TABLE_LISTENING_EVENTS = "recommendation_events"
        const val TABLE_TASTE_PROFILE_SNAPSHOT = "taste_profile_snapshot"
        const val TABLE_RECOMMENDATION_HISTORY = "recommendation_history"
        const val TABLE_NOT_INTERESTED = "not_interested"

        // Columns
        const val COL_SONG_ID = "song_id"
        const val COL_CANONICAL_ID = "canonical_id"
        const val COL_ADDED_AT = "added_at"

        const val COL_PLAYLIST_ID = "id"
        const val COL_PLAYLIST_NAME = "name"
        const val COL_COLOR_INDEX = "color_index"
        const val COL_CREATED_AT = "created_at"

        const val COL_LAST_PLAYED = "last_played"
        const val COL_PLAY_COUNT = "play_count"

        // Downloaded Metadata Columns
        const val COL_META_VIDEO_ID = "video_id"
        const val COL_META_DISPLAY_NAME = "display_name"
        const val COL_META_TITLE = "title"
        const val COL_META_ARTIST = "artist"
        const val COL_META_ALBUM = "album"
        const val COL_META_THUMBNAIL_URI = "thumbnail_uri"
        const val COL_META_DURATION = "duration_ms"
        const val COL_META_LOCAL_URI = "local_uri"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_FAVORITES (
                $COL_SONG_ID INTEGER PRIMARY KEY,
                $COL_ADDED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_PLAYLISTS (
                $COL_PLAYLIST_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PLAYLIST_NAME TEXT NOT NULL,
                $COL_COLOR_INDEX INTEGER DEFAULT 0,
                $COL_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_PLAYLIST_SONGS (
                $COL_PLAYLIST_ID INTEGER NOT NULL,
                $COL_SONG_ID INTEGER NOT NULL,
                $COL_ADDED_AT INTEGER NOT NULL,
                PRIMARY KEY ($COL_PLAYLIST_ID, $COL_SONG_ID)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_HISTORY (
                $COL_SONG_ID INTEGER PRIMARY KEY,
                $COL_LAST_PLAYED INTEGER NOT NULL,
                $COL_PLAY_COUNT INTEGER DEFAULT 1
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_DOWNLOADED_METADATA (
                $COL_META_VIDEO_ID TEXT PRIMARY KEY,
                $COL_META_DISPLAY_NAME TEXT,
                $COL_META_TITLE TEXT NOT NULL,
                $COL_META_ARTIST TEXT NOT NULL,
                $COL_META_ALBUM TEXT,
                $COL_META_THUMBNAIL_URI TEXT,
                $COL_META_DURATION INTEGER DEFAULT 0,
                $COL_META_LOCAL_URI TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_HOME_FEED_CACHE (
                section TEXT NOT NULL,
                position INTEGER NOT NULL,
                video_id TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT,
                thumbnail_url TEXT,
                duration_ms INTEGER DEFAULT 0,
                PRIMARY KEY(section, video_id)
            )
            """.trimIndent()
        )

        // Recommendation Engine Tables
        createRecommendationTables(db)

        // Unified Favorites Table
        createUnifiedFavoritesTable(db)
    }

    private fun createUnifiedFavoritesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_UNIFIED_FAVORITES (
                $COL_CANONICAL_ID TEXT PRIMARY KEY,
                $COL_META_VIDEO_ID TEXT,
                $COL_META_TITLE TEXT NOT NULL,
                $COL_META_ARTIST TEXT NOT NULL,
                $COL_META_ALBUM TEXT,
                $COL_META_THUMBNAIL_URI TEXT,
                $COL_META_DURATION INTEGER DEFAULT 0,
                $COL_META_LOCAL_URI TEXT,
                $COL_ADDED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createRecommendationTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_LISTENING_EVENTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                video_id TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT,
                event_type TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                position_ms INTEGER NOT NULL,
                percentage_listened REAL NOT NULL,
                is_manual INTEGER NOT NULL,
                language TEXT,
                genre TEXT,
                mood TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TASTE_PROFILE_SNAPSHOT (
                dimension TEXT NOT NULL,
                entity_key TEXT NOT NULL,
                affinity_score REAL NOT NULL,
                updated_at INTEGER NOT NULL,
                play_count INTEGER NOT NULL,
                skip_count INTEGER NOT NULL,
                PRIMARY KEY (dimension, entity_key)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_RECOMMENDATION_HISTORY (
                video_id TEXT PRIMARY KEY,
                recommended_at INTEGER NOT NULL,
                section TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NOT_INTERESTED (
                video_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                marked_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_DOWNLOADED_METADATA (
                    $COL_META_VIDEO_ID TEXT PRIMARY KEY,
                    $COL_META_DISPLAY_NAME TEXT,
                    $COL_META_TITLE TEXT NOT NULL,
                    $COL_META_ARTIST TEXT NOT NULL,
                    $COL_META_ALBUM TEXT,
                    $COL_META_THUMBNAIL_URI TEXT,
                    $COL_META_DURATION INTEGER DEFAULT 0,
                    $COL_META_LOCAL_URI TEXT
                )
                """.trimIndent()
            )
        } else if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE $TABLE_DOWNLOADED_METADATA ADD COLUMN $COL_META_LOCAL_URI TEXT")
            } catch (ignored: Exception) {}
        }
        if (oldVersion < 4) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_HOME_FEED_CACHE (
                    section TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    video_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    album TEXT,
                    thumbnail_url TEXT,
                    duration_ms INTEGER DEFAULT 0,
                    PRIMARY KEY(section, video_id)
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 5) {
            createRecommendationTables(db)
        }
        if (oldVersion < 6) {
            createUnifiedFavoritesTable(db)
        }
    }

    // --- Favorites ---
    fun isFavorite(songId: Long): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT 1 FROM $TABLE_FAVORITES WHERE $COL_SONG_ID = ?",
            arrayOf(songId.toString())
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun toggleFavorite(songId: Long): Boolean {
        return if (isFavorite(songId)) {
            val db = writableDatabase
            db.delete(TABLE_FAVORITES, "$COL_SONG_ID = ?", arrayOf(songId.toString()))
            false
        } else {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_SONG_ID, songId)
                put(COL_ADDED_AT, System.currentTimeMillis())
            }
            db.insertWithOnConflict(TABLE_FAVORITES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            true
        }
    }

    fun getAllFavoriteIds(): Set<Long> {
        val ids = mutableSetOf<Long>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT $COL_SONG_ID FROM $TABLE_FAVORITES", null)
        while (cursor.moveToNext()) {
            ids.add(cursor.getLong(0))
        }
        cursor.close()
        return ids
    }

    // --- Unified Favorites (PlayableTrack) ---
    fun isUnifiedFavorite(canonicalId: String): Boolean {
        val db = readableDatabase
        val cleanVideoId = canonicalId.removePrefix("online_")
        val cursor = db.rawQuery(
            "SELECT 1 FROM $TABLE_UNIFIED_FAVORITES WHERE $COL_CANONICAL_ID = ? OR $COL_META_VIDEO_ID = ?",
            arrayOf(canonicalId, cleanVideoId)
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun isTrackFavorite(track: PlayableTrack): Boolean {
        val videoId = track.onlineVideoId
        if (!videoId.isNullOrBlank() && isUnifiedFavorite("online_$videoId")) {
            return true
        }
        if (isUnifiedFavorite(track.id)) {
            return true
        }
        val localSongId = (track as? LocalTrack)?.song?.id ?: (track as? UnifiedTrack)?.localSong?.id
        if (localSongId != null && isFavorite(localSongId)) {
            return true
        }
        return false
    }

    fun toggleTrackFavorite(track: PlayableTrack): Boolean {
        val isCurrentlyFav = isTrackFavorite(track)
        val db = writableDatabase
        val canonicalId = track.onlineVideoId?.let { "online_$it" } ?: track.id
        val videoId = track.onlineVideoId
        val localSongId = (track as? LocalTrack)?.song?.id ?: (track as? UnifiedTrack)?.localSong?.id

        return if (isCurrentlyFav) {
            db.delete(
                TABLE_UNIFIED_FAVORITES,
                "$COL_CANONICAL_ID = ? OR $COL_META_VIDEO_ID = ?",
                arrayOf(canonicalId, videoId ?: canonicalId)
            )
            if (localSongId != null) {
                db.delete(TABLE_FAVORITES, "$COL_SONG_ID = ?", arrayOf(localSongId.toString()))
            }
            false
        } else {
            val values = ContentValues().apply {
                put(COL_CANONICAL_ID, canonicalId)
                put(COL_META_VIDEO_ID, videoId)
                put(COL_META_TITLE, track.title)
                put(COL_META_ARTIST, track.artist)
                put(COL_META_ALBUM, track.album)
                put(COL_META_THUMBNAIL_URI, track.artworkUriString)
                put(COL_META_DURATION, track.durationMs)
                put(COL_META_LOCAL_URI, track.localUri?.toString())
                put(COL_ADDED_AT, System.currentTimeMillis())
            }
            db.insertWithOnConflict(TABLE_UNIFIED_FAVORITES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            if (localSongId != null) {
                val localValues = ContentValues().apply {
                    put(COL_SONG_ID, localSongId)
                    put(COL_ADDED_AT, System.currentTimeMillis())
                }
                db.insertWithOnConflict(TABLE_FAVORITES, null, localValues, SQLiteDatabase.CONFLICT_REPLACE)
            }
            true
        }
    }

    fun getAllUnifiedFavorites(): List<UnifiedTrack> {
        val list = mutableListOf<UnifiedTrack>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_CANONICAL_ID, $COL_META_VIDEO_ID, $COL_META_TITLE, $COL_META_ARTIST, $COL_META_ALBUM, $COL_META_THUMBNAIL_URI, $COL_META_DURATION, $COL_META_LOCAL_URI FROM $TABLE_UNIFIED_FAVORITES ORDER BY $COL_ADDED_AT DESC",
            null
        )
        while (cursor.moveToNext()) {
            val canonicalId = cursor.getString(0)
            val videoId = cursor.getString(1)
            val title = cursor.getString(2)
            val artist = cursor.getString(3)
            val album = cursor.getString(4)
            val artworkUrl = cursor.getString(5)
            val durationMs = cursor.getLong(6)
            val localUriStr = cursor.getString(7)
            val localUri = localUriStr?.let { Uri.parse(it) }

            list.add(
                UnifiedTrack(
                    canonicalId = canonicalId,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    artworkUrl = artworkUrl,
                    onlineVideoId = videoId,
                    localUri = localUri,
                    localSong = null,
                    isFavorite = true
                )
            )
        }
        cursor.close()
        return list
    }

    // --- Playlists ---
    fun createPlaylist(name: String, colorIndex: Int = 0): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_PLAYLIST_NAME, name.trim())
            put(COL_COLOR_INDEX, colorIndex)
            put(COL_CREATED_AT, System.currentTimeMillis())
        }
        return db.insert(TABLE_PLAYLISTS, null, values)
    }

    fun getPlaylists(): List<Playlist> {
        val list = mutableListOf<Playlist>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT p.$COL_PLAYLIST_ID, p.$COL_PLAYLIST_NAME, p.$COL_COLOR_INDEX, p.$COL_CREATED_AT,
                   COUNT(ps.$COL_SONG_ID) AS song_count
            FROM $TABLE_PLAYLISTS p
            LEFT JOIN $TABLE_PLAYLIST_SONGS ps ON p.$COL_PLAYLIST_ID = ps.$COL_PLAYLIST_ID
            GROUP BY p.$COL_PLAYLIST_ID
            ORDER BY p.$COL_CREATED_AT DESC
            """.trimIndent(),
            null
        )
        while (cursor.moveToNext()) {
            list.add(
                Playlist(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    colorIndex = cursor.getInt(2),
                    createdAt = cursor.getLong(3),
                    songCount = cursor.getInt(4)
                )
            )
        }
        cursor.close()
        return list
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_PLAYLIST_ID, playlistId)
            put(COL_SONG_ID, songId)
            put(COL_ADDED_AT, System.currentTimeMillis())
        }
        val result = db.insertWithOnConflict(TABLE_PLAYLIST_SONGS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        return result != -1L
    }

    fun getSongIdsForPlaylist(playlistId: Long): List<Long> {
        val list = mutableListOf<Long>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_SONG_ID FROM $TABLE_PLAYLIST_SONGS WHERE $COL_PLAYLIST_ID = ? ORDER BY $COL_ADDED_AT ASC",
            arrayOf(playlistId.toString())
        )
        while (cursor.moveToNext()) {
            list.add(cursor.getLong(0))
        }
        cursor.close()
        return list
    }

    fun deletePlaylist(playlistId: Long) {
        val db = writableDatabase
        db.delete(TABLE_PLAYLISTS, "$COL_PLAYLIST_ID = ?", arrayOf(playlistId.toString()))
        db.delete(TABLE_PLAYLIST_SONGS, "$COL_PLAYLIST_ID = ?", arrayOf(playlistId.toString()))
    }

    // --- Play Count / History ---
    fun recordSongPlay(songId: Long) {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.execSQL(
            """
            INSERT INTO $TABLE_HISTORY ($COL_SONG_ID, $COL_LAST_PLAYED, $COL_PLAY_COUNT)
            VALUES (?, ?, 1)
            ON CONFLICT($COL_SONG_ID) DO UPDATE SET
                $COL_LAST_PLAYED = ?,
                $COL_PLAY_COUNT = $COL_PLAY_COUNT + 1
            """.trimIndent(),
            arrayOf(songId, now, now)
        )
    }

    fun getRecentSongIds(limit: Int = 30): List<Long> {
        val list = mutableListOf<Long>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_SONG_ID FROM $TABLE_HISTORY ORDER BY $COL_LAST_PLAYED DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        while (cursor.moveToNext()) {
            list.add(cursor.getLong(0))
        }
        cursor.close()
        return list
    }

    // --- Downloaded Song Metadata ---
    fun saveDownloadedMetadata(meta: DownloadedMetadata) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_META_VIDEO_ID, meta.videoId)
            put(COL_META_DISPLAY_NAME, meta.displayName)
            put(COL_META_TITLE, meta.title)
            put(COL_META_ARTIST, meta.artist)
            put(COL_META_ALBUM, meta.album)
            put(COL_META_THUMBNAIL_URI, meta.thumbnailUri)
            put(COL_META_DURATION, meta.durationMs)
            put(COL_META_LOCAL_URI, meta.localUri)
        }
        db.insertWithOnConflict(TABLE_DOWNLOADED_METADATA, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteDownloadedMetadata(videoId: String) {
        val db = writableDatabase
        db.delete(TABLE_DOWNLOADED_METADATA, "$COL_META_VIDEO_ID = ?", arrayOf(videoId))
    }

    fun getDownloadedMetadata(videoId: String): DownloadedMetadata? {
        val db = readableDatabase
        var result: DownloadedMetadata? = null
        try {
            val cursor = db.rawQuery(
                "SELECT $COL_META_VIDEO_ID, $COL_META_DISPLAY_NAME, $COL_META_TITLE, $COL_META_ARTIST, $COL_META_ALBUM, $COL_META_THUMBNAIL_URI, $COL_META_DURATION, $COL_META_LOCAL_URI FROM $TABLE_DOWNLOADED_METADATA WHERE $COL_META_VIDEO_ID = ? LIMIT 1",
                arrayOf(videoId)
            )
            if (cursor.moveToFirst()) {
                result = DownloadedMetadata(
                    videoId = cursor.getString(0) ?: "",
                    displayName = cursor.getString(1) ?: "",
                    title = cursor.getString(2) ?: "",
                    artist = cursor.getString(3) ?: "",
                    album = cursor.getString(4),
                    thumbnailUri = cursor.getString(5),
                    durationMs = cursor.getLong(6),
                    localUri = cursor.getString(7)
                )
            }
            cursor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    fun getAllDownloadedMetadata(): List<DownloadedMetadata> {
        val list = mutableListOf<DownloadedMetadata>()
        val db = readableDatabase
        try {
            val cursor = db.rawQuery(
                "SELECT $COL_META_VIDEO_ID, $COL_META_DISPLAY_NAME, $COL_META_TITLE, $COL_META_ARTIST, $COL_META_ALBUM, $COL_META_THUMBNAIL_URI, $COL_META_DURATION, $COL_META_LOCAL_URI FROM $TABLE_DOWNLOADED_METADATA",
                null
            )
            while (cursor.moveToNext()) {
                list.add(
                    DownloadedMetadata(
                        videoId = cursor.getString(0) ?: "",
                        displayName = cursor.getString(1) ?: "",
                        title = cursor.getString(2) ?: "",
                        artist = cursor.getString(3) ?: "",
                        album = cursor.getString(4),
                        thumbnailUri = cursor.getString(5),
                        durationMs = cursor.getLong(6),
                        localUri = cursor.getString(7)
                    )
                )
            }
            cursor.close()
        } catch (e: Exception) {
            // In case table does not exist
        }
        return list
    }

    // --- Home Feed Cache ---
    fun saveHomeFeedCache(section: String, tracks: List<OnlineTrack>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_HOME_FEED_CACHE, "section = ?", arrayOf(section))
            tracks.forEachIndexed { index, track ->
                val cv = ContentValues().apply {
                    put("section", section)
                    put("position", index)
                    put("video_id", track.videoId)
                    put("title", track.title)
                    put("artist", track.artist)
                    put("album", track.album)
                    put("thumbnail_url", track.thumbnailUrl)
                    put("duration_ms", track.durationMs)
                }
                db.insertWithOnConflict(TABLE_HOME_FEED_CACHE, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            db.endTransaction()
        }
    }

    fun getHomeFeedCache(section: String): List<OnlineTrack> {
        val list = mutableListOf<OnlineTrack>()
        val db = readableDatabase
        try {
            val cursor = db.rawQuery(
                "SELECT video_id, title, artist, album, thumbnail_url, duration_ms FROM $TABLE_HOME_FEED_CACHE WHERE section = ? ORDER BY position ASC",
                arrayOf(section)
            )
            while (cursor.moveToNext()) {
                list.add(
                    OnlineTrack(
                        videoId = cursor.getString(0) ?: "",
                        title = cursor.getString(1) ?: "",
                        artist = cursor.getString(2) ?: "",
                        album = cursor.getString(3),
                        thumbnailUrl = cursor.getString(4),
                        durationMs = cursor.getLong(5)
                    )
                )
            }
            cursor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    // --- Recommendation Engine Helpers ---

    fun insertListeningEvent(event: ListeningEvent): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("session_id", event.playbackSessionId)
            put("video_id", event.videoId)
            put("title", event.title)
            put("artist", event.artist)
            put("album", event.album)
            put("event_type", event.eventType.name)
            put("timestamp", event.timestamp)
            put("duration_ms", event.durationMs)
            put("position_ms", event.positionMs)
            put("percentage_listened", event.percentageListened)
            put("is_manual", if (event.isManual) 1 else 0)
            put("language", event.language)
            put("genre", event.genre)
            put("mood", event.mood)
        }
        return db.insert(TABLE_LISTENING_EVENTS, null, cv)
    }

    fun getRecentListeningEvents(limit: Int = 1000): List<ListeningEvent> {
        val events = mutableListOf<ListeningEvent>()
        val db = readableDatabase
        try {
            val cursor = db.rawQuery(
                """
                SELECT id, session_id, video_id, title, artist, album, event_type, 
                       timestamp, duration_ms, position_ms, percentage_listened, is_manual,
                       language, genre, mood
                FROM $TABLE_LISTENING_EVENTS
                ORDER BY timestamp DESC
                LIMIT ?
                """.trimIndent(),
                arrayOf(limit.toString())
            )
            while (cursor.moveToNext()) {
                val eventTypeStr = cursor.getString(6)
                val eventType = try {
                    EventType.valueOf(eventTypeStr)
                } catch (e: Exception) {
                    EventType.PLAY_STARTED
                }
                events.add(
                    ListeningEvent(
                        id = cursor.getLong(0),
                        playbackSessionId = cursor.getString(1),
                        videoId = cursor.getString(2),
                        title = cursor.getString(3),
                        artist = cursor.getString(4),
                        album = cursor.getString(5),
                        eventType = eventType,
                        timestamp = cursor.getLong(7),
                        durationMs = cursor.getLong(8),
                        positionMs = cursor.getLong(9),
                        percentageListened = cursor.getFloat(10),
                        isManual = cursor.getInt(11) == 1,
                        language = cursor.getString(12),
                        genre = cursor.getString(13),
                        mood = cursor.getString(14)
                    )
                )
            }
            cursor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return events
    }

    fun pruneOldListeningEvents(maxAgeMs: Long, maxCount: Int) {
        val db = writableDatabase
        try {
            val cutoff = System.currentTimeMillis() - maxAgeMs
            db.delete(TABLE_LISTENING_EVENTS, "timestamp < ?", arrayOf(cutoff.toString()))

            // Keep at most maxCount latest events
            db.execSQL(
                """
                DELETE FROM $TABLE_LISTENING_EVENTS 
                WHERE id NOT IN (
                    SELECT id FROM $TABLE_LISTENING_EVENTS 
                    ORDER BY timestamp DESC 
                    LIMIT ?
                )
                """.trimIndent(),
                arrayOf(maxCount)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveTasteSnapshots(dimension: String, scores: Map<String, Float>) {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            db.delete(TABLE_TASTE_PROFILE_SNAPSHOT, "dimension = ?", arrayOf(dimension))
            for ((key, score) in scores) {
                val cv = ContentValues().apply {
                    put("dimension", dimension)
                    put("entity_key", key)
                    put("affinity_score", score)
                    put("updated_at", now)
                    put("play_count", 0)
                    put("skip_count", 0)
                }
                db.insertWithOnConflict(TABLE_TASTE_PROFILE_SNAPSHOT, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            db.endTransaction()
        }
    }

    fun loadTasteSnapshots(dimension: String): Map<String, Float> {
        val result = mutableMapOf<String, Float>()
        val db = readableDatabase
        try {
            val cursor = db.rawQuery(
                "SELECT entity_key, affinity_score FROM $TABLE_TASTE_PROFILE_SNAPSHOT WHERE dimension = ?",
                arrayOf(dimension)
            )
            while (cursor.moveToNext()) {
                val key = cursor.getString(0)
                val score = cursor.getFloat(1)
                result[key] = score
            }
            cursor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    fun recordRecommendationHistory(videoIds: Collection<String>, section: String) {
        if (videoIds.isEmpty()) return
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            for (videoId in videoIds) {
                val cv = ContentValues().apply {
                    put("video_id", videoId)
                    put("recommended_at", now)
                    put("section", section)
                }
                db.insertWithOnConflict(TABLE_RECOMMENDATION_HISTORY, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            // Trim recommendation history to last 200 entries
            db.execSQL(
                """
                DELETE FROM $TABLE_RECOMMENDATION_HISTORY 
                WHERE video_id NOT IN (
                    SELECT video_id FROM $TABLE_RECOMMENDATION_HISTORY 
                    ORDER BY recommended_at DESC 
                    LIMIT 200
                )
                """.trimIndent()
            )
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            db.endTransaction()
        }
    }

    fun getRecentRecommendationHistory(limit: Int = 100): Set<String> {
        val set = mutableSetOf<String>()
        val db = readableDatabase
        try {
            val cursor = db.rawQuery(
                "SELECT video_id FROM $TABLE_RECOMMENDATION_HISTORY ORDER BY recommended_at DESC LIMIT ?",
                arrayOf(limit.toString())
            )
            while (cursor.moveToNext()) {
                set.add(cursor.getString(0))
            }
            cursor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return set
    }

    fun markNotInterested(videoId: String, title: String, artist: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("video_id", videoId)
            put("title", title)
            put("artist", artist)
            put("marked_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_NOT_INTERESTED, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllNotInterestedVideoIds(): Set<String> {
        val set = mutableSetOf<String>()
        val db = readableDatabase
        try {
            val cursor = db.rawQuery("SELECT video_id FROM $TABLE_NOT_INTERESTED", null)
            while (cursor.moveToNext()) {
                set.add(cursor.getString(0))
            }
            cursor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return set
    }
}

data class DownloadedMetadata(
    val videoId: String,
    val displayName: String,
    val title: String,
    val artist: String,
    val album: String?,
    val thumbnailUri: String?,
    val durationMs: Long = 0L,
    val localUri: String? = null
)
