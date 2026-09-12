package com.lyro.app.core.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object ArtworkCache {

    // In-memory cache holding up to 250 decoded thumbnails (~10-12 MB total RAM)
    private val memoryCache = object : LruCache<Long, Bitmap>(250) {
        override fun sizeOf(key: Long, value: Bitmap): Int = 1
    }

    // Negative cache: tracks IDs of songs known to have no embedded artwork
    // Prevents repeated disk I/O when scrolling past tracks without cover art!
    private val noArtworkSet = Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())

    fun get(songId: Long): Bitmap? {
        return memoryCache.get(songId)
    }

    fun hasAttempted(songId: Long): Boolean {
        return memoryCache.get(songId) != null || noArtworkSet.contains(songId)
    }

    fun loadThumbnail(context: Context, songId: Long, uri: Uri): Bitmap? {
        // Fast-path memory check
        memoryCache.get(songId)?.let { return it }
        if (noArtworkSet.contains(songId)) return null

        var result: Bitmap? = null

        // 1. Android Q+ (API 29+) loadThumbnail for direct audio file
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                result = context.contentResolver.loadThumbnail(uri, Size(128, 128), null)
            } catch (ignored: Throwable) {
                // No thumbnail via contentResolver, fallback below
            }
        }

        // 2. Direct MediaMetadataRetriever fallback for ID3 embedded picture
        if (result == null) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val pic = retriever.embeddedPicture
                retriever.release()

                if (pic != null && pic.isNotEmpty()) {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeByteArray(pic, 0, pic.size, options)
                    options.inSampleSize = calculateInSampleSize(options, 128, 128)
                    options.inJustDecodeBounds = false
                    result = BitmapFactory.decodeByteArray(pic, 0, pic.size, options)
                }
            } catch (ignored: Throwable) {
                // File unreadable or no embedded art
            }
        }

        if (result != null) {
            memoryCache.put(songId, result)
        } else {
            noArtworkSet.add(songId)
        }

        return result
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun clear() {
        memoryCache.evictAll()
        noArtworkSet.clear()
    }
}
