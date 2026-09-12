package com.lyro.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.lyro.app.data.model.Playlist

class LyroDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "lyro_music.db"
        private const val DATABASE_VERSION = 1

        // Tables
        const val TABLE_FAVORITES = "favorites"
        const val TABLE_PLAYLISTS = "playlists"
        const val TABLE_PLAYLIST_SONGS = "playlist_songs"
        const val TABLE_HISTORY = "history"

        // Columns
        const val COL_SONG_ID = "song_id"
        const val COL_ADDED_AT = "added_at"

        const val COL_PLAYLIST_ID = "id"
        const val COL_PLAYLIST_NAME = "name"
        const val COL_COLOR_INDEX = "color_index"
        const val COL_CREATED_AT = "created_at"

        const val COL_LAST_PLAYED = "last_played"
        const val COL_PLAY_COUNT = "play_count"
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FAVORITES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PLAYLISTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PLAYLIST_SONGS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
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
}
