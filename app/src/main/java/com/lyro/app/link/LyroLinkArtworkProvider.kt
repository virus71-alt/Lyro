package com.lyro.app.link

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.lyro.app.LyroApplication
import com.lyro.app.core.artwork.ArtworkUtils
import com.lyro.app.data.model.LocalTrack
import com.lyro.app.data.model.OnlineTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Centralized artwork resolver and proxy for Lyro Link.
 * Resolves artwork for:
 * - Local MediaStore tracks (ContentResolver albumart, embedded ID3 pictures, local cache)
 * - Downloaded tracks (downloaded metadata thumbnailUri, local artwork directory)
 * - Online-only tracks (in-memory cached PlayableTrack, unified favorites, YouTube CDN)
 * - Caches remote artwork in cacheDir to avoid redundant network roundtrips.
 */
class LyroLinkArtworkProvider(
    private val context: Context,
    private val libraryProvider: LyroLinkLibraryProvider
) {

    companion object {
        private const val TAG = "LyroLinkArtwork"
        private const val CACHE_SUBDIR = "lyro_link_artwork"
    }

    sealed class ArtworkResult {
        data class FileResult(val file: File, val mimeType: String) : ArtworkResult()
        data class StreamResult(val stream: InputStream, val mimeType: String, val length: Long = -1L) : ArtworkResult()
        data class BytesResult(val bytes: ByteArray, val mimeType: String) : ArtworkResult()
        object Fallback : ArtworkResult()
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_SUBDIR).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Resolves the artwork for the given trackId into a concrete ArtworkResult.
     */
    suspend fun resolveArtwork(trackId: String): ArtworkResult = withContext(Dispatchers.IO) {
        val sanitized = libraryProvider.sanitizeTrackId(trackId) ?: return@withContext ArtworkResult.Fallback

        try {
            if (sanitized.startsWith("local_")) {
                resolveLocalArtwork(sanitized)
            } else if (sanitized.startsWith("yt_")) {
                resolveOnlineOrDownloadedArtwork(sanitized)
            } else {
                Log.d(TAG, "Artwork request: trackId=$sanitized source=FALLBACK")
                ArtworkResult.Fallback
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving artwork for $sanitized: ${e.message}")
            Log.d(TAG, "Artwork request: trackId=$sanitized source=FALLBACK")
            ArtworkResult.Fallback
        }
    }

    /**
     * Resolves local MediaStore track artwork via albumart content URI, embedded ID3 tag, or local file.
     */
    private fun resolveLocalArtwork(sanitized: String): ArtworkResult {
        val songId = sanitized.removePrefix("local_").toLongOrNull() ?: return ArtworkResult.Fallback
        val app = LyroApplication.instance
        val song = app.musicRepository.allSongs.value.firstOrNull { it.id == songId }
            ?: return ArtworkResult.Fallback

        // 1. Check custom albumArtUriString if available
        val artUriStr = song.albumArtUriString
        if (!artUriStr.isNullOrBlank()) {
            try {
                val uri = Uri.parse(artUriStr)
                if (uri.scheme == "file") {
                    val file = File(uri.path ?: "")
                    if (file.exists() && file.length() > 0) {
                        Log.d(TAG, "Artwork request: trackId=$sanitized source=MEDIASTORE")
                        return ArtworkResult.FileResult(file, detectMimeType(file))
                    }
                } else if (uri.scheme == "content") {
                    val stream = context.contentResolver.openInputStream(uri)
                    if (stream != null) {
                        Log.d(TAG, "Artwork request: trackId=$sanitized source=MEDIASTORE")
                        return ArtworkResult.StreamResult(stream, "image/jpeg")
                    }
                }
            } catch (ignored: Exception) {}
        }

        // 2. Standard MediaStore albumart URI
        try {
            val artUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId)
            val stream = context.contentResolver.openInputStream(artUri)
            if (stream != null) {
                Log.d(TAG, "Artwork request: trackId=$sanitized source=MEDIASTORE")
                return ArtworkResult.StreamResult(stream, "image/jpeg")
            }
        } catch (ignored: Exception) {}

        // 3. Embedded artwork in audio file via MediaMetadataRetriever
        try {
            val retriever = MediaMetadataRetriever()
            if (!song.contentUriString.isNullOrBlank()) {
                retriever.setDataSource(context, Uri.parse(song.contentUriString))
            } else {
                val mediaUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
                retriever.setDataSource(context, mediaUri)
            }
            val embedded = retriever.embeddedPicture
            retriever.release()
            if (embedded != null && embedded.isNotEmpty()) {
                val mime = detectMimeTypeFromBytes(embedded)
                Log.d(TAG, "Artwork request: trackId=$sanitized source=MEDIASTORE")
                return ArtworkResult.BytesResult(embedded, mime)
            }
        } catch (ignored: Exception) {}

        // 4. Local filesDir artwork cache by clean title
        try {
            val cleanTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val candidateFile = File(context.filesDir, "artwork/$cleanTitle.jpg")
            if (candidateFile.exists() && candidateFile.length() > 0) {
                Log.d(TAG, "Artwork request: trackId=$sanitized source=MEDIASTORE")
                return ArtworkResult.FileResult(candidateFile, "image/jpeg")
            }
        } catch (ignored: Exception) {}

        Log.d(TAG, "Artwork request: trackId=$sanitized source=FALLBACK")
        return ArtworkResult.Fallback
    }

    /**
     * Resolves online or downloaded track artwork from cache, metadata, or remote thumbnail proxy.
     */
    private suspend fun resolveOnlineOrDownloadedArtwork(sanitized: String): ArtworkResult {
        val videoId = sanitized.removePrefix("yt_")

        // 1. Check disk cache in cacheDir/lyro_link_artwork/
        val cachedInCacheDir = findCachedArtworkFile(cacheDir, videoId)
        if (cachedInCacheDir != null && cachedInCacheDir.length() > 0) {
            Log.d(TAG, "Artwork request: trackId=$sanitized source=CACHE")
            return ArtworkResult.FileResult(cachedInCacheDir, detectMimeType(cachedInCacheDir))
        }

        // 2. Check downloaded artwork in filesDir/artwork/
        val downloadedArtworkDir = File(context.filesDir, "artwork")
        val cachedInFilesDir = findCachedArtworkFile(downloadedArtworkDir, videoId)
        if (cachedInFilesDir != null && cachedInFilesDir.length() > 0) {
            Log.d(TAG, "Artwork request: trackId=$sanitized source=CACHE")
            return ArtworkResult.FileResult(cachedInFilesDir, detectMimeType(cachedInFilesDir))
        }

        // 3. Resolve candidate thumbnail URL from metadata sources
        var candidateUrl: String? = null
        var candidateSource = "ONLINE_TRACK_URL"

        // 3A. In-memory web track cache (online-only recommendations / quick picks)
        val cachedTrack = libraryProvider.getCachedPlayableTrack(sanitized)
        if (cachedTrack is OnlineTrack && !cachedTrack.thumbnailUrl.isNullOrBlank()) {
            candidateUrl = cachedTrack.thumbnailUrl
            candidateSource = "ONLINE_TRACK_URL"
        }

        // 3B. Downloaded metadata
        if (candidateUrl.isNullOrBlank()) {
            val app = LyroApplication.instance
            val meta = app.databaseHelper.getDownloadedMetadata(videoId)
            if (meta != null && !meta.thumbnailUri.isNullOrBlank()) {
                candidateUrl = meta.thumbnailUri
                candidateSource = "DOWNLOADED_METADATA_URL"
            }
        }

        // 3C. Unified favorites metadata
        if (candidateUrl.isNullOrBlank()) {
            val app = LyroApplication.instance
            val favMeta = app.databaseHelper.getUnifiedFavorite("online_$videoId")
            if (favMeta != null && !favMeta.thumbnailUri.isNullOrBlank()) {
                candidateUrl = favMeta.thumbnailUri
                candidateSource = "DOWNLOADED_METADATA_URL"
            }
        }

        // 3D. Default YouTube thumbnail URL construction
        if (candidateUrl.isNullOrBlank()) {
            candidateUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            candidateSource = "ONLINE_TRACK_URL"
        }

        // 4. Upgrade URL to high-resolution (ArtworkUtils)
        val highResUrl = ArtworkUtils.getHighResArtworkUrl(candidateUrl) ?: candidateUrl

        // 5. Proxy and cache remote image
        val fetchResult = fetchAndCacheRemoteImage(videoId, highResUrl, candidateUrl)
        if (fetchResult != null) {
            Log.d(TAG, "Artwork request: trackId=$sanitized source=$candidateSource")
            return ArtworkResult.BytesResult(fetchResult.first, fetchResult.second)
        }

        Log.d(TAG, "Artwork request: trackId=$sanitized source=FALLBACK")
        return ArtworkResult.Fallback
    }

    /**
     * Fetches remote image via OkHttp with fallback URL retry and caches to disk.
     */
    private fun fetchAndCacheRemoteImage(
        videoId: String,
        targetUrl: String,
        fallbackOriginalUrl: String
    ): Pair<ByteArray, String>? {
        // Attempt target high-res URL first
        val firstAttempt = executeHttpImageFetch(targetUrl)
        if (firstAttempt != null) {
            saveToCacheDir(videoId, firstAttempt.first, firstAttempt.second)
            return firstAttempt
        }

        // If high-res URL failed (e.g. 404 for maxresdefault), retry with standard fallback URL
        val fallbackUrl = ArtworkUtils.getFallbackArtworkUrl(targetUrl) ?: fallbackOriginalUrl
        if (fallbackUrl != targetUrl) {
            val secondAttempt = executeHttpImageFetch(fallbackUrl)
            if (secondAttempt != null) {
                saveToCacheDir(videoId, secondAttempt.first, secondAttempt.second)
                return secondAttempt
            }
        }

        return null
    }

    private fun executeHttpImageFetch(url: String): Pair<ByteArray, String>? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }

                val body = response.body ?: return null
                val bytes = body.bytes()
                if (bytes.isEmpty()) return null

                val headerContentType = response.header("Content-Type")?.substringBefore(";")?.trim()
                val mimeType = if (!headerContentType.isNullOrBlank() && headerContentType.startsWith("image/")) {
                    headerContentType
                } else {
                    detectMimeTypeFromBytes(bytes)
                }

                Pair(bytes, mimeType)
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP fetch failed for artwork $url: ${e.message}")
            null
        }
    }

    private fun saveToCacheDir(videoId: String, bytes: ByteArray, mimeType: String) {
        try {
            val ext = when (mimeType) {
                "image/webp" -> "webp"
                "image/png" -> "png"
                else -> "jpg"
            }
            val targetFile = File(cacheDir, "$videoId.$ext")
            targetFile.writeBytes(bytes)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache artwork file for $videoId: ${e.message}")
        }
    }

    private fun findCachedArtworkFile(dir: File, videoId: String): File? {
        if (!dir.exists()) return null
        val candidates = listOf(
            File(dir, "$videoId.webp"),
            File(dir, "$videoId.jpg"),
            File(dir, "$videoId.jpeg"),
            File(dir, "$videoId.png")
        )
        return candidates.firstOrNull { it.exists() && it.length() > 0 }
    }

    private fun detectMimeType(file: File): String {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".webp") -> "image/webp"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".gif") -> "image/gif"
            name.endsWith(".svg") -> "image/svg+xml"
            else -> "image/jpeg"
        }
    }

    private fun detectMimeTypeFromBytes(bytes: ByteArray): String {
        if (bytes.size < 4) return "image/jpeg"
        // JPEG: FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return "image/jpeg"
        }
        // PNG: 89 50 4E 47
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
            return "image/png"
        }
        // WebP: RIFF ... WEBP
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() && bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) {
            return "image/webp"
        }
        return "image/jpeg"
    }
}
