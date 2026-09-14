package com.lyro.app.core.offline

import android.content.Context
import android.net.Uri
import android.util.Log
import com.lyro.app.LyroApplication
import com.lyro.app.core.matcher.LocalMediaIndex
import com.lyro.app.data.download.MusicDownloader
import com.lyro.app.data.local.LyroDatabaseHelper
import com.lyro.app.data.model.LocalTrack
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.data.model.UnifiedTrack
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Authoritative resolver determining whether a track is playable offline.
 * Verifies that backing local URIs/files actually exist on disk and are readable,
 * invalidating stale download mappings if files were externally deleted.
 */
class OfflineAvailabilityResolver(
    private val context: Context? = null,
    private val localMediaIndex: LocalMediaIndex,
    private val musicDownloader: MusicDownloader? = null,
    private val dbHelper: LyroDatabaseHelper? = null
) {
    companion object {
        private const val TAG = "LyroOfflineResolver"
        private const val CACHE_EXPIRATION_MS = 5000L
    }

    private data class CachedCheck(val isAvailable: Boolean, val timestamp: Long)
    private val verificationCache = ConcurrentHashMap<String, CachedCheck>()

    /**
     * Clears cached verification results (e.g. after rescan or download completed).
     */
    fun clearCache() {
        verificationCache.clear()
    }

    /**
     * Checks if [track] is available to play offline with zero internet access.
     */
    fun isAvailableOffline(track: PlayableTrack): Boolean {
        val cacheKey = track.id
        val now = System.currentTimeMillis()
        val cached = verificationCache[cacheKey]
        if (cached != null && (now - cached.timestamp) < CACHE_EXPIRATION_MS) {
            return cached.isAvailable
        }

        val available = checkOfflineAvailabilityInternal(track)
        verificationCache[cacheKey] = CachedCheck(available, now)
        return available
    }

    private fun checkOfflineAvailabilityInternal(track: PlayableTrack): Boolean {
        return when (track) {
            is LocalTrack -> {
                isPathOrUriAccessible(track.song.contentUriString)
            }
            is UnifiedTrack -> {
                // 1. Check direct localUri
                val directUri = track.localUri
                if (directUri != null && isUriAccessible(directUri)) {
                    return true
                }
                // 2. Check localSong contentUriString
                val rawSongStr = track.localSong?.contentUriString
                if (!rawSongStr.isNullOrBlank() && isPathOrUriAccessible(rawSongStr)) {
                    return true
                }
                // 3. Check matched song from LocalMediaIndex
                val matchedSong = localMediaIndex.findLocalMatch(track)
                if (matchedSong != null && isPathOrUriAccessible(matchedSong.contentUriString)) {
                    return true
                }
                false
            }
            is OnlineTrack -> {
                val videoId = track.videoId

                // 1. Authoritative check in LocalMediaIndex
                val storedUriStr = localMediaIndex.getLocalUriForVideoId(videoId)
                if (!storedUriStr.isNullOrBlank()) {
                    if (isPathOrUriAccessible(storedUriStr)) {
                        return true
                    } else {
                        // Stale download mapping! File removed externally.
                        Log.w(TAG, "Stale download detected for videoId=$videoId ($storedUriStr). Invalidating mapping.")
                        localMediaIndex.unregisterDownload(videoId)
                        try {
                            dbHelper?.deleteDownloadedMetadata(videoId)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error cleaning stale download metadata: ${e.message}")
                        }
                    }
                }

                // 2. Check heuristic match in LocalMediaIndex
                val matchedSong = localMediaIndex.findLocalMatch(track)
                if (matchedSong != null && isPathOrUriAccessible(matchedSong.contentUriString)) {
                    return true
                }

                // 3. Fallback check via MusicDownloader
                if (musicDownloader?.isTrackDownloaded(track) == true) {
                    val fallbackMatch = localMediaIndex.findLocalMatch(track)
                    if (fallbackMatch != null && isPathOrUriAccessible(fallbackMatch.contentUriString)) {
                        return true
                    }
                }

                false
            }
        }
    }

    /**
     * Verifies that a path or URI actually exists and is readable.
     */
    fun isPathOrUriAccessible(uriOrPath: String?): Boolean {
        if (uriOrPath.isNullOrBlank()) return false
        val cleanPath = when {
            uriOrPath.startsWith("file://") -> uriOrPath.removePrefix("file://")
            uriOrPath.startsWith("file:") -> uriOrPath.removePrefix("file:")
            else -> uriOrPath
        }
        val file = File(cleanPath)
        if (file.exists() && file.canRead() && file.length() > 0) {
            return true
        }
        val uri = try { Uri.parse(uriOrPath) } catch (_: Throwable) { null }
        return if (uri != null) isUriAccessible(uri) else false
    }

    /**
     * Verifies that a Content or File URI actually exists and is readable.
     */
    fun isUriAccessible(uri: Uri?): Boolean {
        if (uri == null) return false
        return try {
            val scheme = try { uri.scheme } catch (_: Throwable) { null }
            when (scheme) {
                "content" -> {
                    val cr = context?.contentResolver
                    if (cr != null) {
                        cr.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                    } else {
                        val path = uri.toString()
                        val file = File(path)
                        file.exists() && file.canRead() && file.length() > 0
                    }
                }
                "file" -> {
                    val path = (try { uri.path } catch (_: Throwable) { null }) ?: uri.toString()
                    val file = File(path)
                    file.exists() && file.canRead() && file.length() > 0
                }
                else -> {
                    val path = uri.toString()
                    val file = File(path)
                    file.exists() && file.canRead() && file.length() > 0
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Universal extension to check if any [PlayableTrack] is available for offline playback.
 */
fun PlayableTrack.isAvailableOffline(): Boolean {
    return try {
        LyroApplication.instance.offlineAvailabilityResolver.isAvailableOffline(this)
    } catch (e: Exception) {
        // Fallback in case of early initialization or testing
        when (this) {
            is LocalTrack -> true
            is UnifiedTrack -> this.localUri != null
            is OnlineTrack -> this.localUri != null
        }
    }
}
