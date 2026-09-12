package com.lyro.app.data.model

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

    fun formattedDuration(): String {
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

data class LocalTrack(
    val song: Song
) : PlayableTrack {
    override val id: String get() = "local_${song.id}"
    override val title: String get() = song.title
    override val artist: String get() = song.artist
    override val album: String get() = song.album
    override val durationMs: Long get() = song.duration
    override val artworkUriString: String? get() = song.contentUriString
    override val isLocal: Boolean get() = true
    override val isFavorite: Boolean get() = song.isFavorite
}

data class OnlineTrack(
    val videoId: String,
    override val title: String,
    override val artist: String,
    override val album: String? = null,
    override val durationMs: Long = 0L,
    val thumbnailUrl: String? = null,
    override val isFavorite: Boolean = false
) : PlayableTrack {
    override val id: String get() = videoId
    override val artworkUriString: String? get() = thumbnailUrl
    override val isLocal: Boolean get() = false
}

// Extension to easily wrap Song
fun Song.toLocalTrack(): LocalTrack = LocalTrack(this)
