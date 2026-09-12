package com.lyro.app.streaming.youtube

import android.util.Log
import com.lyro.app.data.remote.youtube.InnertubeClient
import com.lyro.app.streaming.*

class YouTubeStreamResolver : StreamResolver {

    companion object {
        private const val TAG = "LyroStreamResolver"
    }

    override suspend fun resolve(
        videoId: String,
        quality: AudioQuality
    ): Result<ResolvedStream> {
        val startTime = System.currentTimeMillis()
        var lastError: Throwable? = null

        val profiles = YouTubeClientProfile.ALL_PROFILES

        for ((index, profile) in profiles.withIndex()) {
            try {
                Log.d(TAG, "Attempting stream resolution for videoId=$videoId with profile=${profile.name} (attempt ${index + 1}/${profiles.size})")

                val playerResponseResult = InnertubeClient.getPlayerResponse(videoId, profile)
                if (playerResponseResult.isFailure) {
                    lastError = playerResponseResult.exceptionOrNull()
                    Log.w(TAG, "Profile ${profile.name} player response error: ${lastError?.message}")
                    continue
                }

                val playerJson = playerResponseResult.getOrNull() ?: continue
                val streamingData = playerJson.optJSONObject("streamingData")
                if (streamingData == null) {
                    val playabilityObj = playerJson.optJSONObject("playabilityStatus")
                    val status = playabilityObj?.optString("status") ?: "UNKNOWN"
                    val reason = playabilityObj?.optString("reason") ?: ""
                    Log.w(TAG, "Profile ${profile.name} no streamingData. playabilityStatus=$status $reason")
                    lastError = Exception("Player status: $status ($reason)")
                    continue
                }

                val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
                if (adaptiveFormats == null || adaptiveFormats.length() == 0) {
                    Log.w(TAG, "Profile ${profile.name} empty adaptiveFormats")
                    continue
                }

                val candidate = StreamFormatSelector.selectBestAudioFormat(adaptiveFormats, quality)
                if (candidate == null) {
                    Log.w(TAG, "Profile ${profile.name} no playable direct audio format found")
                    continue
                }

                // Range probe validation
                val isValid = StreamValidator.validate(candidate.url)
                if (!isValid) {
                    Log.w(TAG, "Profile ${profile.name} format itag=${candidate.itag} failed Range validation probe")
                    continue
                }

                val duration = System.currentTimeMillis() - startTime
                Log.d(
                    TAG,
                    "SUCCESS: Stream resolved in ${duration}ms! videoId=$videoId, itag=${candidate.itag}, mime=${candidate.mimeType}, bitrate=${candidate.bitrate}, client=${profile.name}"
                )

                val expiresAtEpoch = parseExpiresAtEpoch(candidate.url)

                return Result.success(
                    ResolvedStream(
                        url = candidate.url,
                        mimeType = candidate.mimeType,
                        bitrate = candidate.bitrate,
                        contentLength = candidate.contentLength,
                        expiresAtEpochSeconds = expiresAtEpoch,
                        itag = candidate.itag,
                        clientProfileName = profile.name
                    )
                )
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Exception with profile ${profile.name}: ${e.message}")
            }
        }

        val totalDuration = System.currentTimeMillis() - startTime
        val failMsg = "Failed to resolve stream for videoId=$videoId after trying ${profiles.size} profiles (${totalDuration}ms)"
        Log.e(TAG, failMsg, lastError)
        return Result.failure(lastError ?: Exception(failMsg))
    }

    private fun parseExpiresAtEpoch(url: String): Long? {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.getQueryParameter("expire")?.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
