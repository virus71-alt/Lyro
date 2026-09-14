package com.lyro.app.link

import android.content.Context
import android.util.Log
import com.lyro.app.LyroApplication
import com.lyro.app.data.local.DownloadedMetadata
import com.lyro.app.data.model.LocalTrack
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Provides music library catalogs, metadata, and search queries for the Lyro Link Web interface.
 * Strictly sanitizes identifiers and prevents any raw file path exposure or directory traversal.
 */
class LyroLinkLibraryProvider(
    private val context: Context
) {

    companion object {
        private const val TAG = "LyroLinkLibrary"
        private val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_-]{1,64}$")
    }

    data class WebTrack(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val artworkUrl: String,
        val isDownloaded: Boolean,
        val isLocal: Boolean,
        val isFavorite: Boolean,
        val streamUrl: String
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id)
            put("title", title)
            put("artist", artist)
            put("album", album)
            put("durationMs", durationMs)
            put("artworkUrl", artworkUrl)
            put("isDownloaded", isDownloaded)
            put("isLocal", isLocal)
            put("isFavorite", isFavorite)
            put("streamUrl", streamUrl)
        }
    }

    /**
     * Sanitizes and validates incoming track ID to protect against path traversal.
     */
    fun sanitizeTrackId(rawId: String): String? {
        val trimmed = rawId.trim()
        if (trimmed.contains("..") || trimmed.contains("/") || trimmed.contains("\\")) {
            Log.w(TAG, "Blocked path traversal attempt in track ID: $rawId")
            return null
        }
        if (!VALID_ID_REGEX.matches(trimmed)) {
            Log.w(TAG, "Invalid characters in track ID: $rawId")
            return null
        }
        return trimmed
    }

    /**
     * Retrieves all locally accessible tracks (MediaStore + manual downloads + smart downloads).
     */
    suspend fun getLibraryTracks(): List<WebTrack> = withContext(Dispatchers.IO) {
        val app = LyroApplication.instance
        val results = mutableListOf<WebTrack>()
        val seenIds = mutableSetOf<String>()

        // 1. Downloaded tracks (Manual + Smart)
        val downloaded = app.databaseHelper.getAllDownloadedMetadata()
        for (meta in downloaded) {
            val id = "yt_${meta.videoId}"
            if (seenIds.add(id)) {
                results.add(
                    WebTrack(
                        id = id,
                        title = meta.title,
                        artist = meta.artist,
                        album = meta.album ?: "Lyro",
                        durationMs = meta.durationMs,
                        artworkUrl = "/api/artwork/$id",
                        isDownloaded = true,
                        isLocal = false,
                        isFavorite = isTrackFavorite(meta.videoId, meta.title, meta.artist),
                        streamUrl = "/stream/$id"
                    )
                )
            }
        }

        // 2. Local MediaStore tracks
        val localSongs = app.musicRepository.allSongs.value
        for (song in localSongs) {
            val id = "local_${song.id}"
            if (seenIds.add(id)) {
                results.add(
                    WebTrack(
                        id = id,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        durationMs = song.duration,
                        artworkUrl = "/api/artwork/$id",
                        isDownloaded = true,
                        isLocal = true,
                        isFavorite = song.isFavorite,
                        streamUrl = "/stream/$id"
                    )
                )
            }
        }

        results
    }

    /**
     * Retrieves personalized Quick Picks for the Web UI.
     */
    suspend fun getQuickPicks(): List<WebTrack> = withContext(Dispatchers.IO) {
        val app = LyroApplication.instance
        val webTracks = mutableListOf<WebTrack>()

        try {
            val isOnline = app.networkMonitor.isOnline.value
            val picks = app.recommendationEngine.getPersonalizedQuickPicks(20)
            for (track in picks) {
                // If phone is offline, only include if track is downloaded or local
                val isDownloaded = app.musicDownloader.isTrackDownloaded(track)
                val isLocal = track is LocalTrack
                if (!isOnline && !isDownloaded && !isLocal) continue

                val webTrack = playableTrackToWebTrack(track, isDownloaded, isLocal)
                if (webTrack != null) {
                    webTracks.add(webTrack)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error generating quick picks: ${e.message}")
        }

        // Fallback: If quick picks empty or offline, use top library tracks
        if (webTracks.isEmpty()) {
            webTracks.addAll(getLibraryTracks().take(20))
        }

        webTracks
    }

    /**
     * Retrieves liked/favorite tracks.
     */
    suspend fun getLikedTracks(): List<WebTrack> = withContext(Dispatchers.IO) {
        val app = LyroApplication.instance
        val localSongs = app.musicRepository.allSongs.value.filter { it.isFavorite }
        val webTracks = mutableListOf<WebTrack>()

        for (song in localSongs) {
            val id = "local_${song.id}"
            webTracks.add(
                WebTrack(
                    id = id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    durationMs = song.duration,
                    artworkUrl = "/api/artwork/$id",
                    isDownloaded = true,
                    isLocal = true,
                    isFavorite = true,
                    streamUrl = "/stream/$id"
                )
            )
        }

        val downloaded = app.databaseHelper.getAllDownloadedMetadata()
        for (meta in downloaded) {
            if (isTrackFavorite(meta.videoId, meta.title, meta.artist)) {
                val id = "yt_${meta.videoId}"
                if (webTracks.none { it.id == id }) {
                    webTracks.add(
                        WebTrack(
                            id = id,
                            title = meta.title,
                            artist = meta.artist,
                            album = meta.album ?: "Lyro",
                            durationMs = meta.durationMs,
                            artworkUrl = "/api/artwork/$id",
                            isDownloaded = true,
                            isLocal = false,
                            isFavorite = true,
                            streamUrl = "/stream/$id"
                        )
                    )
                }
            }
        }

        webTracks
    }

    /**
     * Search tracks by query across local library and downloaded songs.
     */
    suspend fun search(query: String): List<WebTrack> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.isBlank()) return@withContext emptyList()

        val library = getLibraryTracks()
        library.filter {
            it.title.lowercase().contains(q) ||
            it.artist.lowercase().contains(q) ||
            it.album.lowercase().contains(q)
        }
    }

    /**
     * Resolves a WebTrack ID to its underlying PlayableTrack.
     */
    suspend fun resolveTrack(id: String): PlayableTrack? = withContext(Dispatchers.IO) {
        val sanitized = sanitizeTrackId(id) ?: return@withContext null
        val app = LyroApplication.instance

        if (sanitized.startsWith("local_")) {
            val songId = sanitized.removePrefix("local_").toLongOrNull() ?: return@withContext null
            val song = app.musicRepository.allSongs.value.firstOrNull { it.id == songId }
            return@withContext song?.let { LocalTrack(it) }
        } else if (sanitized.startsWith("yt_")) {
            val videoId = sanitized.removePrefix("yt_")
            val meta = app.databaseHelper.getDownloadedMetadata(videoId)
            if (meta != null) {
                return@withContext OnlineTrack(
                    videoId = meta.videoId,
                    title = meta.title,
                    artist = meta.artist,
                    album = meta.album,
                    durationMs = meta.durationMs,
                    thumbnailUrl = meta.thumbnailUri
                )
            }
            // If not downloaded but valid videoId
            return@withContext OnlineTrack(
                videoId = videoId,
                title = "Online Track",
                artist = "YouTube Music",
                album = "YouTube Music",
                durationMs = 0L,
                thumbnailUrl = null
            )
        }
        null
    }

    /**
     * Toggles favorite status for a given track ID in the unified Lyro database.
     */
    suspend fun toggleFavorite(id: String): Boolean = withContext(Dispatchers.IO) {
        val track = resolveTrack(id) ?: return@withContext false
        LyroApplication.instance.musicRepository.toggleFavoriteTrack(track)
    }

    private fun isTrackFavorite(track: PlayableTrack): Boolean {
        return LyroApplication.instance.databaseHelper.isTrackFavorite(track)
    }

    private fun isTrackFavorite(videoId: String, title: String, artist: String): Boolean {
        val app = LyroApplication.instance
        if (videoId.isNotBlank() && app.databaseHelper.isUnifiedFavorite("online_$videoId")) {
            return true
        }
        return app.musicRepository.allSongs.value.any { it.isFavorite && (it.title.equals(title, ignoreCase = true) || it.artist.equals(artist, ignoreCase = true)) }
    }

    private fun playableTrackToWebTrack(track: PlayableTrack, isDownloaded: Boolean, isLocal: Boolean): WebTrack? {
        val id = when {
            track is LocalTrack -> "local_${track.song.id}"
            track.onlineVideoId != null -> "yt_${track.onlineVideoId}"
            track is OnlineTrack -> "yt_${track.videoId}"
            else -> null
        } ?: return null

        return WebTrack(
            id = id,
            title = track.title,
            artist = track.artist,
            album = track.album ?: "Lyro",
            durationMs = track.durationMs,
            artworkUrl = "/api/artwork/$id",
            isDownloaded = isDownloaded,
            isLocal = isLocal,
            isFavorite = isTrackFavorite(track),
            streamUrl = "/stream/$id"
        )
    }
}
