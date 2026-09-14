package com.lyro.app.data.model

import android.net.Uri
import java.util.Locale

sealed interface PlayableTrack {
    val id: String
    val title: String
    val artist: String
    val album: String?
    val durationMs: Long
    val artworkUriString: String?
    val isLocal: Boolean
    val isFavorite: Boolean
    val onlineVideoId: String? get() = null
    val localUri: Uri? get() = null
    val isDownloaded: Boolean get() = localUri != null

    fun formattedDuration(): String {
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

data class LocalTrack(
    val song: Song,
    override val onlineVideoId: String? = null
) : PlayableTrack {
    override val id: String get() = onlineVideoId?.let { "online_$it" } ?: "local_${song.id}"
    override val title: String get() = song.title
    override val artist: String get() = song.artist
    override val album: String get() = song.album
    override val durationMs: Long get() = song.duration
    override val artworkUriString: String? get() = song.contentUriString
    override val isLocal: Boolean get() = true
    override val isFavorite: Boolean get() = song.isFavorite
    override val localUri: Uri? get() = try { song.contentUri } catch (_: Throwable) { null }
    override val isDownloaded: Boolean get() = true
}

data class OnlineTrack(
    val videoId: String,
    override val title: String,
    override val artist: String,
    override val album: String? = null,
    override val durationMs: Long = 0L,
    val thumbnailUrl: String? = null,
    override val isFavorite: Boolean = false,
    override val localUri: Uri? = null
) : PlayableTrack {
    override val id: String get() = videoId
    override val onlineVideoId: String get() = videoId
    override val artworkUriString: String? get() = highResThumbnailUrl ?: thumbnailUrl
    override val isLocal: Boolean get() = localUri != null
    override val isDownloaded: Boolean get() = localUri != null

    val highResThumbnailUrl: String?
        get() = com.lyro.app.core.artwork.ArtworkUtils.getHighResArtworkUrl(thumbnailUrl)
}

// Extension to easily wrap Song
fun Song.toLocalTrack(): LocalTrack = LocalTrack(this)
