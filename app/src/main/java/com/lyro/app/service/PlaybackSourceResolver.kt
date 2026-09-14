package com.lyro.app.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.lyro.app.core.matcher.LocalMediaIndex
import com.lyro.app.core.network.NetworkMonitor
import com.lyro.app.core.offline.OfflineAvailabilityResolver
import com.lyro.app.data.model.LocalTrack
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.model.PlayableTrack
import com.lyro.app.data.model.Song
import java.io.File

/**
 * Result of resolving a playable source for any track in the unified catalog.
 */
sealed interface PlaybackSource {
    data class Local(
        val uri: Uri? = null,
        val localSong: Song? = null,
        val originalTrack: PlayableTrack
    ) : PlaybackSource

    data class Online(
        val videoId: String,
        val originalTrack: PlayableTrack
    ) : PlaybackSource

    data class Unavailable(
        val reason: String,
        val originalTrack: PlayableTrack
    ) : PlaybackSource
}

/**
 * Centralized decision point for playback. Decides whether a track should be played
 * from local storage (MediaStore or downloaded) with zero network calls,
 * streamed online from YouTube Music, or reported as unavailable when offline.
 */
class PlaybackSourceResolver(
    private val context: Context? = null,
    private val localMediaIndex: LocalMediaIndex,
    private val networkMonitor: NetworkMonitor? = null,
    private val offlineAvailabilityResolver: OfflineAvailabilityResolver? = null
) {
    companion object {
        private const val TAG = "LyroPlaybackResolver"
    }

    /**
     * Resolves the authoritative playback source for [track].
     */
    fun resolve(track: PlayableTrack): PlaybackSource {
        val canonicalId = track.onlineVideoId ?: track.id

        // 1. Attempt local resolution first (local-first playback)
        val localSong = localMediaIndex.findLocalMatch(track) ?: (track as? LocalTrack)?.song
        if (localSong != null) {
            val isAccessible = offlineAvailabilityResolver?.isPathOrUriAccessible(localSong.contentUriString)
                ?: isPathAccessible(localSong.contentUriString)
            if (isAccessible) {
                val localUri = try { Uri.parse(localSong.contentUriString) } catch (_: Throwable) { null }
                Log.i(
                    TAG,
                    "Track: ${track.title} | CanonicalId: $canonicalId | LocalMatch: true | PlaybackSource: LOCAL (matched song id=${localSong.id})"
                )
                return PlaybackSource.Local(localUri, localSong, track)
            } else {
                Log.w(
                    TAG,
                    "Matched local song id=${localSong.id} is inaccessible (${localSong.contentUriString}). Falling back..."
                )
            }
        }

        // 2. Check explicit localUri on the track if available
        val directLocalUri = track.localUri
        if (directLocalUri != null) {
            if (isUriAccessible(directLocalUri)) {
                Log.i(
                    TAG,
                    "Track: ${track.title} | CanonicalId: $canonicalId | LocalMatch: true | PlaybackSource: LOCAL (direct uri)"
                )
                return PlaybackSource.Local(directLocalUri, null, track)
            } else {
                Log.w(
                    TAG,
                    "Direct local URI for track \"${track.title}\" is inaccessible ($directLocalUri). Falling back..."
                )
            }
        }

        // 3. Fallback to online streaming ONLY if network is online
        val isOnline = networkMonitor?.isOnline?.value ?: isNetworkAvailable()
        val videoId = track.onlineVideoId ?: if (track is OnlineTrack) track.videoId else null
        if (!videoId.isNullOrBlank()) {
            if (!isOnline) {
                Log.w(
                    TAG,
                    "Track: ${track.title} | CanonicalId: $canonicalId | LocalMatch: false | PlaybackSource: UNAVAILABLE (Offline)"
                )
                return PlaybackSource.Unavailable(
                    reason = "Device is offline and no local copy is available for \"${track.title}\"",
                    originalTrack = track
                )
            }

            Log.i(
                TAG,
                "Track: ${track.title} | CanonicalId: $canonicalId | LocalMatch: false | PlaybackSource: ONLINE"
            )
            return PlaybackSource.Online(videoId, track)
        }

        // 4. No local file and no online identity
        Log.w(
            TAG,
            "Track: ${track.title} | CanonicalId: $canonicalId | LocalMatch: false | PlaybackSource: UNAVAILABLE (No source)"
        )
        return PlaybackSource.Unavailable(
            reason = "No playable audio source found for \"${track.title}\"",
            originalTrack = track
        )
    }

    /**
     * Checks if a Content or File URI is currently accessible for reading.
     */
    fun isUriAccessible(uri: Uri): Boolean {
        if (offlineAvailabilityResolver != null) {
            return offlineAvailabilityResolver.isUriAccessible(uri)
        }
        return try {
            when (uri.scheme) {
                "content" -> {
                    context?.contentResolver?.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                }
                "file" -> {
                    val path = uri.path ?: return false
                    val file = File(path)
                    file.exists() && file.canRead() && file.length() > 0
                }
                else -> {
                    val file = File(uri.toString())
                    file.exists() && file.canRead() && file.length() > 0
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if active network connectivity is present.
     */
    fun isNetworkAvailable(): Boolean {
        if (networkMonitor != null) {
            return networkMonitor.isOnline.value
        }
        val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNet = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNet) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isPathAccessible(path: String): Boolean {
        return try {
            val cleanPath = when {
                path.startsWith("file://") -> path.removePrefix("file://")
                path.startsWith("file:") -> path.removePrefix("file:")
                else -> path
            }
            val file = File(cleanPath)
            if (file.exists() && file.canRead() && file.length() > 0) return true
            val uri = try { Uri.parse(path) } catch (_: Throwable) { null }
            if (uri != null) isUriAccessible(uri) else false
        } catch (_: Exception) {
            false
        }
    }
}

