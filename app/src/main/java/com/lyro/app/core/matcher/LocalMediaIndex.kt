package com.lyro.app.core.matcher

import android.net.Uri
import android.util.Log
import com.lyro.app.data.local.DownloadedMetadata
import com.lyro.app.data.model.LocalTrack
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.data.model.Song
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * High-performance, in-memory index for matching online tracks to local MediaStore / downloaded files.
 * Provides both authoritative lookup (by videoId / stored URI) and conservative heuristic fallback
 * (normalized title + artist + duration tolerance + modifier protection).
 */
class LocalMediaIndex {

    companion object {
        private const val TAG = "LocalMediaIndex"
    }

    // Authoritative mappings: videoId -> Song & videoId -> contentUri
    private val videoIdToSong = ConcurrentHashMap<String, Song>()
    private val videoIdToUri = ConcurrentHashMap<String, String>()
    private val uriToSong = ConcurrentHashMap<String, Song>()

    // Cached snapshot of all device songs for heuristic scanning
    private val cachedLocalSongs = CopyOnWriteArrayList<Song>()

    /**
     * Rebuilds the in-memory index using current MediaStore songs and SQLite downloaded metadata.
     */
    fun rebuild(songs: List<Song>, downloadedMetadataList: List<DownloadedMetadata>) {
        videoIdToSong.clear()
        videoIdToUri.clear()
        uriToSong.clear()
        cachedLocalSongs.clear()
        cachedLocalSongs.addAll(songs)

        val songByUri = songs.associateBy { it.contentUriString }
        uriToSong.putAll(songByUri)

        for (meta in downloadedMetadataList) {
            val videoId = meta.videoId
            if (meta.localUri != null) {
                videoIdToUri[videoId] = meta.localUri
            }

            // Match downloaded metadata to MediaStore Song
            val matchedSong = songs.find { song ->
                (meta.localUri != null && song.contentUriString == meta.localUri) ||
                        (meta.displayName.isNotBlank() && song.title.equals(meta.title, ignoreCase = true) && song.artist.equals(meta.artist, ignoreCase = true)) ||
                        (song.title.contains(meta.title, ignoreCase = true) && song.artist.contains(meta.artist, ignoreCase = true))
            }

            if (matchedSong != null) {
                videoIdToSong[videoId] = matchedSong
                videoIdToUri[videoId] = matchedSong.contentUriString
            }
        }

        Log.d(TAG, "Rebuilt index: ${cachedLocalSongs.size} songs, ${videoIdToSong.size} videoId mappings")
    }

    /**
     * Registers a newly completed download in the index.
     */
    fun registerDownload(videoId: String, uri: Uri, song: Song? = null) {
        val uriStr = uri.toString()
        videoIdToUri[videoId] = uriStr
        if (song != null) {
            videoIdToSong[videoId] = song
            uriToSong[uriStr] = song
        }
        Log.d(TAG, "Registered download: videoId=$videoId -> uri=$uriStr")
    }

    /**
     * Unregisters a deleted download from the index.
     */
    fun unregisterDownload(videoId: String) {
        val removedUri = videoIdToUri.remove(videoId)
        videoIdToSong.remove(videoId)
        Log.d(TAG, "Unregistered download: videoId=$videoId (was $removedUri)")
    }

    /**
     * Finds a local matching [Song] for the given [track].
     * 1. If track is already a [LocalTrack], returns its song.
     * 2. If track has an authoritative [PlayableTrack.onlineVideoId], checks videoId mapping.
     * 3. If track has a [PlayableTrack.localUri], checks URI mapping.
     * 4. Otherwise performs conservative normalized metadata matching against cached songs.
     */
    fun findLocalMatch(track: PlayableTrack): Song? {
        if (track is LocalTrack) {
            return track.song
        }

        // 1. Authoritative lookup by videoId
        val videoId = track.onlineVideoId
        if (!videoId.isNullOrBlank()) {
            videoIdToSong[videoId]?.let { return it }
        }

        // 2. Lookup by explicit localUri
        val localUri = track.localUri
        if (localUri != null) {
            val uriString = localUri.toString()
            uriToSong[uriString]?.let { return it }
            // Try matching cached songs by URI
            cachedLocalSongs.find { it.contentUriString == uriString }?.let { return it }
        }

        // 3. Conservative metadata fallback
        for (candidate in cachedLocalSongs) {
            if (TrackMetadataNormalizer.matches(
                    title1 = candidate.title,
                    artist1 = candidate.artist,
                    duration1 = candidate.duration,
                    title2 = track.title,
                    artist2 = track.artist,
                    duration2 = track.durationMs
                )
            ) {
                // If matched heuristically, cache for this session if videoId is known
                if (!videoId.isNullOrBlank()) {
                    videoIdToSong[videoId] = candidate
                    videoIdToUri[videoId] = candidate.contentUriString
                }
                Log.d(
                    TAG,
                    "Heuristic match found: \"${track.title}\" by \"${track.artist}\" -> local \"${candidate.title}\" (${candidate.contentUriString})"
                )
                return candidate
            }
        }

        return null
    }

    /**
     * Checks if a local copy (downloaded or matching local track) is known for the track.
     */
    fun hasLocalCopy(track: PlayableTrack): Boolean {
        return findLocalMatch(track) != null
    }

    /**
     * Retrieves stored local URI string for a videoId if known.
     */
    fun getLocalUriForVideoId(videoId: String): String? {
        return videoIdToUri[videoId] ?: videoIdToSong[videoId]?.contentUriString
    }
}
