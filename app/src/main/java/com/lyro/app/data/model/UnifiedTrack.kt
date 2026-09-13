package com.lyro.app.data.model

import android.net.Uri

data class UnifiedTrack(
    val canonicalId: String,
    override val title: String,
    override val artist: String,
    override val album: String? = null,
    override val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    override val onlineVideoId: String? = null,
    override val localUri: Uri? = null,
    val localSong: Song? = null,
    override val isFavorite: Boolean = localSong?.isFavorite ?: false
) : PlayableTrack {
    override val id: String get() = canonicalId
    override val artworkUriString: String?
        get() = artworkUrl ?: localSong?.albumArtUriString ?: localSong?.contentUriString
    override val isLocal: Boolean get() = localUri != null
    override val isDownloaded: Boolean get() = localUri != null
}

fun Song.toUnifiedTrack(matchedVideoId: String? = null, highResArt: String? = null): UnifiedTrack {
    val canonId = matchedVideoId?.let { "online_$it" } ?: "local_$id"
    return UnifiedTrack(
        canonicalId = canonId,
        title = title,
        artist = artist,
        album = album,
        durationMs = duration,
        artworkUrl = highResArt ?: albumArtUriString ?: contentUriString,
        onlineVideoId = matchedVideoId,
        localUri = contentUri,
        localSong = this,
        isFavorite = isFavorite
    )
}

fun OnlineTrack.toUnifiedTrack(localSong: Song? = null, matchedUri: Uri? = null): UnifiedTrack {
    return UnifiedTrack(
        canonicalId = "online_$videoId",
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        artworkUrl = highResThumbnailUrl ?: thumbnailUrl,
        onlineVideoId = videoId,
        localUri = matchedUri ?: localSong?.contentUri,
        localSong = localSong,
        isFavorite = isFavorite || (localSong?.isFavorite == true)
    )
}

fun PlayableTrack.toUnifiedTrack(): UnifiedTrack {
    return when (this) {
        is UnifiedTrack -> this
        is LocalTrack -> this.song.toUnifiedTrack(this.onlineVideoId)
        is OnlineTrack -> this.toUnifiedTrack(null, this.localUri)
    }
}

fun UnifiedTrack.toSong(): Song {
    if (localSong != null) return localSong
    return Song(
        id = (onlineVideoId?.hashCode() ?: canonicalId.hashCode()).toLong(),
        title = title,
        artist = artist,
        album = album ?: "Lyro Music",
        albumId = 0L,
        duration = durationMs,
        contentUriString = localUri?.toString() ?: artworkUrl ?: "",
        albumArtUriString = artworkUriString,
        size = 0L,
        dateAdded = 0L,
        isFavorite = isFavorite
    )
}

