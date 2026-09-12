package com.lyro.app.streaming

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object StreamValidator {
    private const val TAG = "LyroStreamValidation"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun validate(
        url: String,
        headers: Map<String, String> = emptyMap(),
        contentLength: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Probe 1: initial chunk
            val requestBuilder1 = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-524287")

            headers.forEach { (k, v) ->
                requestBuilder1.header(k, v)
            }

            val isFirstChunkOk = client.newCall(requestBuilder1.build()).execute().use { response ->
                val code = response.code
                val ok = code in 200..206
                if (!ok) {
                    Log.w(TAG, "Probe chunk 1 (0-512KB) failed: HTTP $code")
                }
                ok
            }

            if (!isFirstChunkOk) return@withContext false

            // Probe 2: verify beyond 1MB if contentLength allows (detects 1MB preview-capped streams)
            if (contentLength != null && contentLength > 1048576L) {
                val secondStart = 1048576L
                val secondEnd = minOf(contentLength - 1L, secondStart + 524287L)
                val requestBuilder2 = Request.Builder()
                    .url(url)
                    .header("Range", "bytes=$secondStart-$secondEnd")

                headers.forEach { (k, v) ->
                    requestBuilder2.header(k, v)
                }

                val isSecondChunkOk = client.newCall(requestBuilder2.build()).execute().use { response ->
                    val code = response.code
                    val ok = code in 200..206
                    if (!ok) {
                        Log.w(TAG, "Probe chunk 2 ($secondStart-$secondEnd) failed: HTTP $code (stream has 1MB cap)")
                    }
                    ok
                }

                if (!isSecondChunkOk) return@withContext false
            }

            Log.d(TAG, "Stream validation probe SUCCESS for URL (playable=true)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Stream validation probe failed with exception: ${e.message}")
            false
        }
    }
}
