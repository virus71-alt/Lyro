package com.lyro.app.data.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.repository.MusicRepository
import com.lyro.app.streaming.AudioQuality
import com.lyro.app.streaming.StreamResolver
import com.lyro.app.streaming.youtube.YouTubeStreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

sealed interface DownloadStatus {
    object Idle : DownloadStatus
    data class Downloading(val progress: Float) : DownloadStatus
    object Completed : DownloadStatus
    data class Failed(val error: String) : DownloadStatus
}

class MusicDownloader(
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val streamResolver: StreamResolver = YouTubeStreamResolver()
) {
    companion object {
        private const val TAG = "LyroDownloader"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _downloadStatuses = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStatuses: StateFlow<Map<String, DownloadStatus>> = _downloadStatuses.asStateFlow()

    private val _lastCompletedTrack = MutableStateFlow<OnlineTrack?>(null)
    val lastCompletedTrack: StateFlow<OnlineTrack?> = _lastCompletedTrack.asStateFlow()

    fun clearLastCompletedTrack() {
        _lastCompletedTrack.value = null
    }

    fun isTrackDownloaded(track: OnlineTrack): Boolean {
        val currentLocalSongs = musicRepository.allSongs.value
        val trackTitle = track.title.trim().lowercase()
        val trackArtist = track.artist.trim().lowercase()
        return currentLocalSongs.any { localSong ->
            val localTitle = localSong.title.trim().lowercase()
            val localArtist = localSong.artist.trim().lowercase()
            (localTitle == trackTitle || localTitle.contains(trackTitle) || trackTitle.contains(localTitle)) &&
                    (localArtist == trackArtist || localArtist.contains(trackArtist) || trackArtist.contains(localArtist))
        }
    }

    suspend fun downloadTrack(track: OnlineTrack): Result<Unit> = withContext(Dispatchers.IO) {
        val videoId = track.videoId
        val currentStatus = _downloadStatuses.value[videoId]
        if (currentStatus is DownloadStatus.Downloading) {
            return@withContext Result.success(Unit)
        }

        updateStatus(videoId, DownloadStatus.Downloading(0.01f))

        try {
            Log.d(TAG, "Resolving stream for download: videoId=$videoId (${track.title})")
            val resolveResult = streamResolver.resolve(videoId, AudioQuality.HIGH)
            if (resolveResult.isFailure) {
                val err = resolveResult.exceptionOrNull()?.message ?: "Could not resolve audio stream"
                Log.e(TAG, "Resolution failed: $err")
                updateStatus(videoId, DownloadStatus.Failed(err))
                return@withContext Result.failure(Exception(err))
            }

            val stream = resolveResult.getOrThrow()
            updateStatus(videoId, DownloadStatus.Downloading(0.05f))

            // Determine file extension
            val containerMime = stream.mimeType?.substringBefore(";")?.trim() ?: "audio/mp4"
            val extension = when {
                containerMime.contains("webm", ignoreCase = true) || containerMime.contains("opus", ignoreCase = true) -> "opus"
                containerMime.contains("mp3", ignoreCase = true) -> "mp3"
                else -> "m4a"
            }

            // Sanitize file name
            val cleanTitle = track.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val cleanArtist = track.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val baseFileName = if (cleanArtist.isNotBlank()) "$cleanTitle - $cleanArtist" else cleanTitle
            val fileName = "$baseFileName.$extension"

            // Download and cache high-res artwork thumbnail locally
            var localThumbnailUri: String? = null
            val targetThumbUrl = track.highResThumbnailUrl ?: track.thumbnailUrl
            if (!targetThumbUrl.isNullOrBlank()) {
                try {
                    val thumbReq = Request.Builder().url(targetThumbUrl).build()
                    val thumbResp = okHttpClient.newCall(thumbReq).execute()
                    if (thumbResp.isSuccessful) {
                        val artworkDir = File(context.filesDir, "artwork")
                        if (!artworkDir.exists()) artworkDir.mkdirs()
                        val thumbFile = File(artworkDir, "${track.videoId}.jpg")
                        thumbFile.outputStream().use { out ->
                            thumbResp.body?.byteStream()?.copyTo(out)
                        }
                        localThumbnailUri = Uri.fromFile(thumbFile).toString()
                        try {
                            thumbFile.copyTo(File(artworkDir, "$cleanTitle.jpg"), overwrite = true)
                        } catch (ignored: Exception) {}
                        Log.d(TAG, "Thumbnail saved to $localThumbnailUri")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to download thumbnail for ${track.title}: ${e.message}")
                }
            }

            // Record authoritative metadata in SQLite database
            val dbHelper = com.lyro.app.data.local.LyroDatabaseHelper(context)
            dbHelper.saveDownloadedMetadata(
                com.lyro.app.data.local.DownloadedMetadata(
                    videoId = track.videoId,
                    displayName = fileName,
                    title = track.title,
                    artist = track.artist,
                    album = track.album ?: "YouTube Music",
                    thumbnailUri = localThumbnailUri ?: track.thumbnailUrl,
                    durationMs = track.durationMs
                )
            )

            // Build request with required YouTube streaming headers
            val requestBuilder = Request.Builder().url(stream.url)
            stream.requestHeaders.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }
            val request = requestBuilder.build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorMsg = "HTTP error ${response.code}: ${response.message}"
                updateStatus(videoId, DownloadStatus.Failed(errorMsg))
                return@withContext Result.failure(Exception(errorMsg))
            }

            val body = response.body ?: throw Exception("Empty download response body")
            val totalBytes = if (body.contentLength() > 0) body.contentLength() else (stream.contentLength ?: -1L)

            // Save via MediaStore (API 29+) or public external storage (API < 29)
            val writtenUri: Uri?
            val outputStream: OutputStream?

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.TITLE, track.title)
                    put(MediaStore.Audio.Media.ARTIST, track.artist)
                    put(MediaStore.Audio.Media.ALBUM, track.album ?: "YouTube Music")
                    put(MediaStore.Audio.Media.MIME_TYPE, containerMime)
                    put(MediaStore.Audio.Media.IS_MUSIC, 1)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Lyro")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                    if (track.durationMs > 0) {
                        put(MediaStore.Audio.Media.DURATION, track.durationMs)
                    }
                }
                val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw Exception("Failed to create MediaStore entry")
                writtenUri = uri
                outputStream = context.contentResolver.openOutputStream(uri)
                    ?: throw Exception("Failed to open output stream for $uri")
            } else {
                @Suppress("DEPRECATION")
                val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Lyro")
                if (!musicDir.exists()) musicDir.mkdirs()
                val targetFile = File(musicDir, fileName)
                writtenUri = Uri.fromFile(targetFile)
                outputStream = FileOutputStream(targetFile)
            }

            // Stream bytes with progress reporting
            val inputStream: InputStream = body.byteStream()
            var bytesWritten = 0L
            val buffer = ByteArray(8192)
            var lastReportedPercent = 5

            outputStream.use { out ->
                inputStream.use { inStream ->
                    var bytesRead: Int
                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        bytesWritten += bytesRead
                        if (totalBytes > 0) {
                            val percent = ((bytesWritten.toFloat() / totalBytes) * 90 + 5).toInt().coerceIn(5, 95)
                            if (percent > lastReportedPercent) {
                                lastReportedPercent = percent
                                updateStatus(videoId, DownloadStatus.Downloading(percent / 100f))
                            }
                        }
                    }
                    out.flush()
                }
            }

            // Finalize entry in MediaStore
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && writtenUri != null) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                    put(MediaStore.Audio.Media.TITLE, track.title)
                    put(MediaStore.Audio.Media.ARTIST, track.artist)
                    put(MediaStore.Audio.Media.ALBUM, track.album ?: "YouTube Music")
                }
                context.contentResolver.update(writtenUri, updateValues, null, null)
            } else if (writtenUri != null) {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(writtenUri.path),
                    arrayOf(containerMime),
                    null
                )
            }

            Log.d(TAG, "Download completed for $fileName ($bytesWritten bytes)")
            updateStatus(videoId, DownloadStatus.Completed)
            _lastCompletedTrack.value = track

            // Refresh local songs so the track immediately shows in Lyro's OFFLINE library
            musicRepository.loadSongs()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Download error for ${track.title}: ${e.message}", e)
            updateStatus(videoId, DownloadStatus.Failed(e.localizedMessage ?: "Download failed"))
            Result.failure(e)
        }
    }

    private fun updateStatus(videoId: String, status: DownloadStatus) {
        val map = _downloadStatuses.value.toMutableMap()
        map[videoId] = status
        _downloadStatuses.value = map
    }
}
