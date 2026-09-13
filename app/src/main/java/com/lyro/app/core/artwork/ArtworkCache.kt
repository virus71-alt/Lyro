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

    // Fast in-memory thumbnail cache for list rows (~128x128)
    private val thumbnailCache = object : LruCache<Long, Bitmap>(250) {
        override fun sizeOf(key: Long, value: Bitmap): Int = 1
    }

    // Dedicated high-resolution cache for active Now Playing screens (~1024x1024)
    private val highResCache = object : LruCache<Long, Bitmap>(15) {
        override fun sizeOf(key: Long, value: Bitmap): Int = 1
    }

    // Negative cache: tracks IDs of songs known to have no embedded artwork
    private val noArtworkSet = Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())

    fun get(songId: Long, highRes: Boolean = false): Bitmap? {
        return if (highRes) {
            highResCache.get(songId) ?: thumbnailCache.get(songId)
        } else {
            thumbnailCache.get(songId)
        }
    }

    fun hasAttempted(songId: Long, highRes: Boolean = false): Boolean {
        return if (highRes) {
            highResCache.get(songId) != null || noArtworkSet.contains(songId)
        } else {
            thumbnailCache.get(songId) != null || noArtworkSet.contains(songId)
        }
    }

    fun loadThumbnail(context: Context, songId: Long, uri: Uri): Bitmap? {
        return loadArtwork(context, songId, uri, highRes = false)
    }

    fun loadArtwork(context: Context, songId: Long, uri: Uri, highRes: Boolean = false): Bitmap? {
        val targetCache = if (highRes) highResCache else thumbnailCache
        targetCache.get(songId)?.let { return it }
        if (noArtworkSet.contains(songId)) return null

        var result: Bitmap? = null
        val targetDimension = if (highRes) 1024 else 128

        // 1. Android Q+ (API 29+) loadThumbnail for direct audio file
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                result = context.contentResolver.loadThumbnail(uri, Size(targetDimension, targetDimension), null)
            } catch (ignored: Throwable) {
                // Fallback to MediaMetadataRetriever below
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
                    options.inSampleSize = calculateInSampleSize(options, targetDimension, targetDimension)
                    options.inJustDecodeBounds = false
                    result = BitmapFactory.decodeByteArray(pic, 0, pic.size, options)
                }
            } catch (ignored: Throwable) {
                // File unreadable or no embedded art
            }
        }

        if (result != null) {
            targetCache.put(songId, result)
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
        thumbnailCache.evictAll()
        highResCache.evictAll()
        noArtworkSet.clear()
    }
}
