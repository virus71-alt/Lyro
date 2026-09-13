package com.lyro.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.lyro.app.data.local.LyroDatabaseHelper
import com.lyro.app.data.model.Playlist
import com.lyro.app.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.lyro.app.core.matcher.LocalMediaIndex

enum class SortOrder {
    TITLE,
    ARTIST,
    DATE_ADDED,
    DURATION
}

class MusicRepository(
    private val context: Context,
    private val dbHelper: LyroDatabaseHelper,
    private val localMediaIndex: LocalMediaIndex? = null
) {


    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songList = mutableListOf<Song>()
        val favoriteIds = dbHelper.getAllFavoriteIds()

        val downloadedMetadataList = dbHelper.getAllDownloadedMetadata()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DISPLAY_NAME
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
                val displayNameCol = it.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val rawTitle = it.getString(titleCol) ?: "Unknown Track"
                    val rawArtist = it.getString(artistCol) ?: "Unknown Artist"
                    val rawAlbum = it.getString(albumCol) ?: "Unknown Album"
                    val albumId = it.getLong(albumIdCol)
                    val duration = it.getLong(durationCol)
                    val size = it.getLong(sizeCol)
                    val dateAdded = it.getLong(dateAddedCol)
                    val displayName = if (displayNameCol >= 0) it.getString(displayNameCol).orEmpty() else ""

                    var title = rawTitle
                    var artist = if (rawArtist.contains("<unknown>", ignoreCase = true)) "Unknown Artist" else rawArtist
                    var album = rawAlbum
                    var albumArtUriString: String? = null

                    // 1. Authoritative lookup from our downloaded SQLite database
                    val meta = downloadedMetadataList.find { m ->
                        (displayName.isNotBlank() && m.displayName.equals(displayName, ignoreCase = true)) ||
                        (displayName.isNotBlank() && displayName.contains(m.title, ignoreCase = true) && displayName.contains(m.artist, ignoreCase = true)) ||
                        (m.title.equals(rawTitle, ignoreCase = true) && m.artist.equals(artist, ignoreCase = true)) ||
                        (rawTitle.contains(m.title, ignoreCase = true) && rawTitle.contains(m.artist, ignoreCase = true))
                    }

                    if (meta != null) {
                        title = meta.title
                        artist = meta.artist
                        if (!meta.album.isNullOrBlank()) album = meta.album
                        albumArtUriString = meta.thumbnailUri
                    } else {
                        // 2. Intelligent fallback: if artist is Unknown and title has " - " (e.g. "Saibo - Sachin-Jigar")
                        if ((artist.isBlank() || artist == "Unknown Artist") && title.contains(" - ")) {
                            val parts = title.split(" - ", limit = 2)
                            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                                title = parts[0].trim()
                                artist = parts[1].trim()
                            }
                        }
                    }

                    // 3. If no custom thumbnail found yet, check files/artwork or standard MediaStore albumart
                    if (albumArtUriString == null) {
                        val cleanT = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
                        val candidateFile = java.io.File(context.filesDir, "artwork/$cleanT.jpg")
                        if (candidateFile.exists()) {
                            albumArtUriString = Uri.fromFile(candidateFile).toString()
                        } else {
                            albumArtUriString = ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"),
                                albumId
                            ).toString()
                        }
                    }

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    songList.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = albumId,
                            duration = duration,
                            contentUriString = contentUri,
                            albumArtUriString = albumArtUriString,
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
        localMediaIndex?.rebuild(songList, downloadedMetadataList)
        refreshPlaylists()
        autoRecoverMissingThumbnails(songList)
        songList
    }

    private fun autoRecoverMissingThumbnails(songs: List<Song>) {
        repositoryScope.launch {
            val missingArtSongs = songs.filter { song ->
                val art = song.albumArtUriString
                val hasValidArt = art != null && (art.startsWith("file:") || art.startsWith("http"))
                !hasValidArt && song.artist != "Unknown Artist"
            }

            if (missingArtSongs.isEmpty()) return@launch

            val okHttpClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            for (song in missingArtSongs) {
                try {
                    val searchResult = com.lyro.app.data.remote.youtube.InnertubeClient.searchSongs("${song.title} ${song.artist}")
                    val firstTrack = searchResult.getOrNull()?.firstOrNull()
                    if (firstTrack != null && !firstTrack.thumbnailUrl.isNullOrBlank()) {
                        val thumbReq = okhttp3.Request.Builder().url(firstTrack.thumbnailUrl).build()
                        val thumbResp = okHttpClient.newCall(thumbReq).execute()
                        if (thumbResp.isSuccessful) {
                            val artworkDir = java.io.File(context.filesDir, "artwork")
                            if (!artworkDir.exists()) artworkDir.mkdirs()
                            val thumbFile = java.io.File(artworkDir, "${firstTrack.videoId}.jpg")
                            thumbFile.outputStream().use { out ->
                                thumbResp.body?.byteStream()?.copyTo(out)
                            }
                            val cleanT = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
                            try {
                                thumbFile.copyTo(java.io.File(artworkDir, "$cleanT.jpg"), overwrite = true)
                            } catch (ignored: Exception) {}

                            val thumbUri = Uri.fromFile(thumbFile).toString()
                            dbHelper.saveDownloadedMetadata(
                                com.lyro.app.data.local.DownloadedMetadata(
                                    videoId = firstTrack.videoId,
                                    displayName = "${cleanT}.opus",
                                    title = song.title,
                                    artist = song.artist,
                                    album = song.album,
                                    thumbnailUri = thumbUri,
                                    durationMs = song.duration
                                )
                            )
                            // Update in-memory list
                            _allSongs.value = _allSongs.value.map {
                                if (it.id == song.id) it.copy(albumArtUriString = thumbUri) else it
                            }
                        }
                    }
                } catch (ignored: Exception) {
                }
            }
        }
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
