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
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun validate(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-524287")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                val isPlayable = code in 200..206
                Log.d(TAG, "Stream probe result: HTTP $code, playable=$isPlayable")
                isPlayable
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stream validation probe failed: ${e.message}")
            false
        }
    }
}
