package com.lyro.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.lyro.app.data.local.LyroDatabaseHelper
import com.lyro.app.data.model.Playlist
import com.lyro.app.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class SortOrder {
    TITLE,
    ARTIST,
    DATE_ADDED,
    DURATION
}

class MusicRepository(
    private val context: Context,
    private val dbHelper: LyroDatabaseHelper
) {

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songList = mutableListOf<Song>()
        val favoriteIds = dbHelper.getAllFavoriteIds()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Unknown Track"
                    val artist = it.getString(artistCol) ?: "Unknown Artist"
                    val album = it.getString(albumCol) ?: "Unknown Album"
                    val albumId = it.getLong(albumIdCol)
                    val duration = it.getLong(durationCol)
                    val size = it.getLong(sizeCol)
                    val dateAdded = it.getLong(dateAddedCol)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    songList.add(
                        Song(
                            id = id,
                            title = title,
                            artist = if (artist.contains("<unknown>", ignoreCase = true)) "Unknown Artist" else artist,
                            album = album,
                            albumId = albumId,
                            duration = duration,
                            contentUriString = contentUri,
                            albumArtUriString = null,
                            size = size,
                            dateAdded = dateAdded,
                            isFavorite = favoriteIds.contains(id)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _allSongs.value = songList
        refreshPlaylists()
        songList
    }

    suspend fun refreshPlaylists() = withContext(Dispatchers.IO) {
        _playlists.value = dbHelper.getPlaylists()
    }

    suspend fun toggleFavorite(song: Song): Boolean = withContext(Dispatchers.IO) {
        val newState = dbHelper.toggleFavorite(song.id)
        _allSongs.value = _allSongs.value.map {
            if (it.id == song.id) it.copy(isFavorite = newState) else it
        }
        newState
    }

    suspend fun createPlaylist(name: String, colorIndex: Int): Long = withContext(Dispatchers.IO) {
        val id = dbHelper.createPlaylist(name, colorIndex)
        refreshPlaylists()
        id
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long): Boolean = withContext(Dispatchers.IO) {
        val success = dbHelper.addSongToPlaylist(playlistId, songId)
        refreshPlaylists()
        success
    }

    suspend fun getSongsForPlaylist(playlistId: Long): List<Song> = withContext(Dispatchers.IO) {
        val songIds = dbHelper.getSongIdsForPlaylist(playlistId).toSet()
        _allSongs.value.filter { songIds.contains(it.id) }
    }

    suspend fun getFavoriteSongs(): List<Song> = withContext(Dispatchers.IO) {
        _allSongs.value.filter { it.isFavorite }
    }

    suspend fun recordPlayed(songId: Long) = withContext(Dispatchers.IO) {
        dbHelper.recordSongPlay(songId)
    }

    fun filterAndSort(
        query: String,
        sortOrder: SortOrder,
        favoritesOnly: Boolean = false
    ): List<Song> {
        val list = _allSongs.value.filter { song ->
            val matchesQuery = query.isBlank() ||
                    song.title.contains(query, ignoreCase = true) ||
                    song.artist.contains(query, ignoreCase = true) ||
                    song.album.contains(query, ignoreCase = true)
            val matchesFav = !favoritesOnly || song.isFavorite
            matchesQuery && matchesFav
        }

        return when (sortOrder) {
            SortOrder.TITLE -> list.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> list.sortedBy { it.artist.lowercase() }
            SortOrder.DATE_ADDED -> list.sortedByDescending { it.dateAdded }
            SortOrder.DURATION -> list.sortedByDescending { it.duration }
        }
    }
}
