package com.lyro.app.streaming.youtube

import android.util.Log
import com.lyro.app.core.network.NetworkMonitor
import com.lyro.app.data.remote.youtube.InnertubeClient
import com.lyro.app.streaming.*

class YouTubeStreamResolver(
    private val networkMonitor: NetworkMonitor? = null
) : StreamResolver {

    companion object {
        private const val TAG = "LyroStreamResolver"
    }

    override suspend fun resolve(
        videoId: String,
        quality: AudioQuality,
        excludeProfiles: Set<String>
    ): Result<ResolvedStream> {
        val isOnline = networkMonitor?.isOnline?.value
            ?: try { com.lyro.app.LyroApplication.instance.networkMonitor.isOnline.value } catch (e: Exception) { true }
        if (!isOnline) {
            Log.w(TAG, "Stream resolution aborted: device is offline for videoId=$videoId")
            return Result.failure(OfflineException("Cannot stream online audio: Device is offline"))
        }

        val startTime = System.currentTimeMillis()
        var lastError: Throwable? = null

        val candidateProfiles = YouTubeClientProfile.ALL_PROFILES.filter { it.name !in excludeProfiles }
        if (candidateProfiles.isEmpty()) {
            return Result.failure(Exception("All available stream profiles have been excluded or failed for videoId=$videoId"))
        }


        for ((index, profile) in candidateProfiles.withIndex()) {
            try {
                Log.d(
                    TAG,
                    "Attempting stream resolution for videoId=$videoId with profile=${profile.name} (candidate ${index + 1}/${candidateProfiles.size})"
                )

                val playerResponseResult = InnertubeClient.getPlayerResponse(videoId, profile)
                if (playerResponseResult.isFailure) {
                    lastError = playerResponseResult.exceptionOrNull()
                    Log.w(TAG, "Profile ${profile.name} player response failed: ${lastError?.message}")
                    continue
                }

                val playerJson = playerResponseResult.getOrNull() ?: continue
                val playabilityObj = playerJson.optJSONObject("playabilityStatus")
                val status = playabilityObj?.optString("status") ?: "UNKNOWN"
                val reason = playabilityObj?.optString("reason") ?: ""

                val streamingData = playerJson.optJSONObject("streamingData")
                if (streamingData == null) {
                    Log.w(TAG, "Profile ${profile.name} no streamingData. playabilityStatus=$status $reason")
                    lastError = Exception("Player status: $status ($reason)")
                    continue
                }

                val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
                if (adaptiveFormats == null || adaptiveFormats.length() == 0) {
                    Log.w(TAG, "Profile ${profile.name} empty adaptiveFormats (playability=$status)")
                    continue
                }

                Log.d(
                    TAG,
                    "Profile ${profile.name}: playability=$status, total adaptiveFormats=${adaptiveFormats.length()}"
                )

                val candidate = StreamFormatSelector.selectBestAudioFormat(adaptiveFormats, quality)
                if (candidate == null) {
                    Log.w(TAG, "Profile ${profile.name} has no direct playable audio format URL")
                    continue
                }

                val streamHeaders = profile.headersForStream(candidate.url)

                // Range probe validation (checks initial chunk and verifies whole-file capability past 1MB)
                val isValid = StreamValidator.validate(
                    url = candidate.url,
                    headers = streamHeaders,
                    contentLength = candidate.contentLength
                )
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
                        clientProfileName = profile.name,
                        requestHeaders = streamHeaders
                    )
                )
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Exception with profile ${profile.name}: ${e.message}")
            }
        }

        val totalDuration = System.currentTimeMillis() - startTime
        val failMsg = "Failed to resolve stream for videoId=$videoId after trying ${candidateProfiles.size} profiles (${totalDuration}ms)"
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
