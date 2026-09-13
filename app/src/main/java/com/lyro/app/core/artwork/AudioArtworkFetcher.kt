package com.lyro.app.core.artwork

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioArtworkFetcher(
    private val context: Context,
    private val uri: Uri
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        // 1. Android Q+ (API 29+) loadThumbnail for specific audio URI
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val bitmap = context.contentResolver.loadThumbnail(uri, Size(1024, 1024), null)
                return@withContext DrawableResult(
                    drawable = BitmapDrawable(context.resources, bitmap),
                    isSampled = false,
                    dataSource = DataSource.DISK
                )
            } catch (e: Throwable) {
                // Not found or no embedded artwork, try fallback
            }
        }

        // 2. MediaMetadataRetriever fallback for ID3 embedded picture
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val embeddedPic = retriever.embeddedPicture
            retriever.release()

            if (embeddedPic != null && embeddedPic.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeByteArray(embeddedPic, 0, embeddedPic.size)
                if (bitmap != null) {
                    return@withContext DrawableResult(
                        drawable = BitmapDrawable(context.resources, bitmap),
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                }
            }
        } catch (e: Throwable) {
            // No embedded artwork
        }

        null
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme
            val isAudioUri = (scheme == "content" && (data.authority == "media" || data.path?.contains("audio") == true))
                    || scheme == "file"
            if (isAudioUri) {
                return AudioArtworkFetcher(context, data)
            }
            return null
        }
    }
}
