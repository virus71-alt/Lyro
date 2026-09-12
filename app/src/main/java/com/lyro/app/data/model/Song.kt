package com.lyro.app.data.model

import android.net.Uri
import java.util.Locale

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long, // milliseconds
    val contentUriString: String,
    val albumArtUriString: String?,
    val size: Long,
    val dateAdded: Long,
    val isFavorite: Boolean = false
) {
    val contentUri: Uri
        get() = Uri.parse(contentUriString)

    // Direct unique audio URI used to extract exact embedded cover art
    val artworkUri: Uri
        get() = contentUri

    val albumArtUri: Uri
        get() = contentUri

    fun formattedDuration(): String {
        val totalSeconds = duration / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    fun formattedSize(): String {
        val mb = size.toDouble() / (1024 * 1024)
        return String.format(Locale.getDefault(), "%.1f MB", mb)
    }
}
