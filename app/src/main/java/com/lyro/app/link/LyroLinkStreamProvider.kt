package com.lyro.app.link

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.lyro.app.LyroApplication
import com.lyro.app.streaming.AudioQuality
import com.lyro.app.streaming.youtube.YouTubeStreamResolver
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Handles audio byte streaming for Lyro Link clients.
 * Supports:
 * - Local MediaStore tracks (content:// URIs)
 * - Downloaded manual & Smart Download tracks (local files)
 * - Online proxy streams via YouTubeStreamResolver + OkHttp progressive chunking
 * - Full RFC 7233 HTTP Range Requests (206 Partial Content) for instant browser seeking
 */
class LyroLinkStreamProvider(
    private val context: Context,
    private val libraryProvider: LyroLinkLibraryProvider
) {

    companion object {
        private const val TAG = "LyroLinkStream"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val streamResolver = YouTubeStreamResolver()

    /**
     * Resolves the requested track and produces a NanoHTTPD Response (200 OK or 206 Partial Content).
     */
    fun serveStream(trackId: String, headers: Map<String, String>): Response {
        val sanitizedId = libraryProvider.sanitizeTrackId(trackId)
            ?: return NanoHTTPD.newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid track ID")

        val rangeHeader = headers["range"] ?: headers["Range"]

        return try {
            if (sanitizedId.startsWith("local_")) {
                serveLocalMediaStoreTrack(sanitizedId.removePrefix("local_"), rangeHeader)
            } else if (sanitizedId.startsWith("yt_")) {
                serveOnlineOrDownloadedTrack(sanitizedId.removePrefix("yt_"), rangeHeader)
            } else {
                NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Unknown track identifier")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Streaming error for $sanitizedId: ${e.message}", e)
            NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Streaming failed: ${e.message}")
        }
    }

    private fun serveLocalMediaStoreTrack(songIdStr: String, rangeHeader: String?): Response {
        val songId = songIdStr.toLongOrNull()
            ?: return NanoHTTPD.newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid song ID")

        val app = LyroApplication.instance
        val song = app.musicRepository.allSongs.value.firstOrNull { it.id == songId }
            ?: return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Track not found in library")

        val contentUri = song.contentUri
        val mimeType = inferMimeType(song.title, contentUri.toString())

        return serveFromUri(contentUri, mimeType, rangeHeader)
    }

    private fun serveOnlineOrDownloadedTrack(videoId: String, rangeHeader: String?): Response {
        val app = LyroApplication.instance
        val downloadedMeta = app.databaseHelper.getDownloadedMetadata(videoId)

        // 1. Check if track is downloaded locally on the device
        if (downloadedMeta?.localUri != null) {
            val localUri = Uri.parse(downloadedMeta.localUri)
            val mimeType = inferMimeType(
                downloadedMeta.displayName.ifBlank { downloadedMeta.title },
                downloadedMeta.localUri
            )
            return serveFromUri(localUri, mimeType, rangeHeader)
        }

        // 2. Check offline status
        val isOnline = app.networkMonitor.isOnline.value
        if (!isOnline) {
            Log.w(TAG, "Track $videoId is not downloaded and phone is offline")
            return NanoHTTPD.newFixedLengthResponse(
                Response.Status.SERVICE_UNAVAILABLE,
                "text/plain",
                "Phone is offline and this track is not downloaded locally"
            )
        }

        // 3. Online proxy stream
        return proxyOnlineStream(videoId, rangeHeader)
    }

    /**
     * Streams audio from a content:// or file:// URI with HTTP Range support.
     */
    private fun serveFromUri(uri: Uri, mimeType: String, rangeHeader: String?): Response {
        val pfd: ParcelFileDescriptor = try {
            context.contentResolver.openFileDescriptor(uri, "r")
                ?: return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Cannot open media source")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open descriptor for $uri: ${e.message}")
            return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File descriptor unavailable")
        }

        val totalLength = pfd.statSize
        if (totalLength <= 0) {
            pfd.close()
            return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Invalid file length")
        }

        val (start, end) = parseRangeHeader(rangeHeader, totalLength)

        val chunkLength = end - start + 1
        val fis = FileInputStream(pfd.fileDescriptor)
        fis.channel.position(start)

        // Bounded stream that closes both FileInputStream and ParcelFileDescriptor when completed
        val boundedStream = object : InputStream() {
            private var bytesRemaining = chunkLength

            override fun read(): Int {
                if (bytesRemaining <= 0) return -1
                val b = fis.read()
                if (b != -1) bytesRemaining--
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (bytesRemaining <= 0) return -1
                val maxToRead = minOf(len.toLong(), bytesRemaining).toInt()
                val read = fis.read(b, off, maxToRead)
                if (read != -1) bytesRemaining -= read
                return read
            }

            override fun close() {
                try { fis.close() } catch (ignored: Exception) {}
                try { pfd.close() } catch (ignored: Exception) {}
            }
        }

        val isPartial = rangeHeader != null && (start > 0 || end < totalLength - 1)
        val status = if (isPartial) Response.Status.PARTIAL_CONTENT else Response.Status.OK

        val response = NanoHTTPD.newFixedLengthResponse(status, mimeType, boundedStream, chunkLength)
        response.addHeader("Accept-Ranges", "bytes")
        if (isPartial) {
            response.addHeader("Content-Range", "bytes $start-$end/$totalLength")
        }
        response.addHeader("Content-Length", chunkLength.toString())
        return response
    }

    /**
     * Proxies online audio stream from YouTube Music via StreamResolver.
     */
    private fun proxyOnlineStream(videoId: String, rangeHeader: String?): Response {
        val resolved = kotlinx.coroutines.runBlocking {
            streamResolver.resolve(videoId, AudioQuality.AUTO)
        }

        if (resolved.isFailure) {
            val err = resolved.exceptionOrNull()?.message ?: "Stream resolution failed"
            return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", err)
        }

        val stream = resolved.getOrThrow()
        val mimeType = stream.mimeType?.substringBefore(";")?.trim() ?: "audio/mp4"

        val reqBuilder = Request.Builder().url(stream.url)
        stream.requestHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

        if (!rangeHeader.isNullOrBlank()) {
            reqBuilder.addHeader("Range", rangeHeader)
        }

        val okResponse = okHttpClient.newCall(reqBuilder.build()).execute()
        if (!okResponse.isSuccessful) {
            okResponse.close()
            return NanoHTTPD.newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Upstream stream error: ${okResponse.code}"
            )
        }

        val body = okResponse.body
            ?: return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Empty stream body")

        val upstreamStream = body.byteStream()
        val contentLength = body.contentLength()

        // Auto-close OkHttp response on stream close
        val wrappedStream = object : InputStream() {
            override fun read(): Int = upstreamStream.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = upstreamStream.read(b, off, len)
            override fun close() {
                try { upstreamStream.close() } catch (ignored: Exception) {}
                try { okResponse.close() } catch (ignored: Exception) {}
            }
        }

        val status = if (okResponse.code == 206) Response.Status.PARTIAL_CONTENT else Response.Status.OK
        val resp = if (contentLength > 0) {
            NanoHTTPD.newFixedLengthResponse(status, mimeType, wrappedStream, contentLength)
        } else {
            NanoHTTPD.newChunkedResponse(status, mimeType, wrappedStream)
        }

        resp.addHeader("Accept-Ranges", "bytes")
        val upstreamContentRange = okResponse.header("Content-Range")
        if (upstreamContentRange != null) {
            resp.addHeader("Content-Range", upstreamContentRange)
        }
        if (contentLength > 0) {
            resp.addHeader("Content-Length", contentLength.toString())
        }

        return resp
    }

    private fun parseRangeHeader(rangeHeader: String?, totalLength: Long): Pair<Long, Long> {
        if (rangeHeader.isNullOrBlank()) {
            return Pair(0L, totalLength - 1)
        }

        val cleanRange = rangeHeader.trim()
        if (!cleanRange.startsWith("bytes=")) {
            return Pair(0L, totalLength - 1)
        }

        val spec = cleanRange.removePrefix("bytes=").trim()
        val parts = spec.split("-")

        var start = 0L
        var end = totalLength - 1

        if (parts.size >= 1 && parts[0].isNotBlank()) {
            start = parts[0].toLongOrNull() ?: 0L
        }
        if (parts.size >= 2 && parts[1].isNotBlank()) {
            end = parts[1].toLongOrNull() ?: (totalLength - 1)
        }

        start = start.coerceIn(0L, totalLength - 1)
        end = end.coerceIn(start, totalLength - 1)

        return Pair(start, end)
    }

    private fun inferMimeType(name: String, uriString: String): String {
        val target = "$name $uriString".lowercase()
        return when {
            target.contains(".mp3") -> "audio/mpeg"
            target.contains(".flac") -> "audio/flac"
            target.contains(".m4a") || target.contains(".mp4") || target.contains(".aac") -> "audio/mp4"
            target.contains(".ogg") || target.contains(".opus") -> "audio/ogg"
            target.contains(".wav") -> "audio/wav"
            else -> "audio/mpeg"
        }
    }
}
