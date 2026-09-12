package com.lyro.app.data.remote.youtube

import android.util.Log
import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.streaming.youtube.YouTubeClientProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object InnertubeClient {
    private const val TAG = "LyroOnlineSearch"
    private const val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val VISITOR_ID_URL = "https://music.youtube.com/youtubei/v1/visitor_id"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    private var cachedVisitorData: String? = null
    private val visitorMutex = Mutex()

    /**
     * Retrieve or reuse visitorData for YouTube API authentication
     */
    private suspend fun getVisitorData(): String? = withContext(Dispatchers.IO) {
        cachedVisitorData?.let { return@withContext it }
        visitorMutex.withLock {
            cachedVisitorData?.let { return@withLock it }
            try {
                val visitorRequestBody = JSONObject().apply {
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", "WEB_REMIX")
                            put("clientVersion", "1.20240901.01.00")
                            put("hl", "en")
                            put("gl", "US")
                        })
                    })
                }

                val request = Request.Builder()
                    .url(VISITOR_ID_URL)
                    .post(visitorRequestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                    .header("Origin", "https://music.youtube.com")
                    .header("X-YouTube-Client-Name", "67")
                    .header("X-YouTube-Client-Version", "1.20240901.01.00")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val visitor = json.optJSONObject("responseContext")?.optString("visitorData")
                        if (!visitor.isNullOrBlank()) {
                            cachedVisitorData = visitor
                            Log.d(TAG, "Acquired YouTube visitorData successfully")
                            return@withLock visitor
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch visitorData: ${e.message}")
            }
            null
        }
    }

    suspend fun searchSongs(query: String): Result<List<OnlineTrack>> = withContext(Dispatchers.IO) {
        try {
            val visitorData = getVisitorData()
            val requestBodyJson = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240901.01.00")
                        put("hl", "en")
                        put("gl", "US")
                        if (!visitorData.isNullOrBlank()) {
                            put("visitorData", visitorData)
                        }
                    })
                })
                put("query", query)
                // Filter specifically for Songs in YouTube Music
                put("params", "Eg-KAQwIARAAGAAgACgAMABqChAEEAMQCRAFEAo%3D")
            }

            val request = Request.Builder()
                .url(SEARCH_URL)
                .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .apply {
                    if (!visitorData.isNullOrBlank()) {
                        header("X-Goog-Visitor-Id", visitorData)
                    }
                }
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = "YouTube Music search failed with HTTP ${response.code}"
                    Log.e(TAG, msg)
                    return@withContext Result.failure(Exception(msg))
                }

                val bodyString = response.body?.string() ?: return@withContext Result.success(emptyList())
                val rootJson = JSONObject(bodyString)
                val tracks = parseSearchResponse(rootJson)
                Log.d(TAG, "Search for \"$query\" returned ${tracks.size} tracks")
                Result.success(tracks)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search network error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun parseSearchResponse(root: JSONObject): List<OnlineTrack> {
        val results = mutableListOf<OnlineTrack>()
        try {
            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs") ?: return results

            val tab = tabs.optJSONObject(0)?.optJSONObject("tabRenderer") ?: return results
            val sectionList = tab.optJSONObject("content")?.optJSONObject("sectionListRenderer") ?: return results
            val sectionContents = sectionList.optJSONArray("contents") ?: return results

            for (i in 0 until sectionContents.length()) {
                val section = sectionContents.optJSONObject(i) ?: continue
                val musicShelf = section.optJSONObject("musicShelfRenderer")
                    ?: section.optJSONObject("musicCardShelfRenderer")
                    ?: continue

                val items = musicShelf.optJSONArray("contents") ?: continue
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    val track = parseTrackItem(item)
                    if (track != null) {
                        results.add(track)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing search results JSON: ${e.message}")
        }
        return results
    }

    private fun parseTrackItem(item: JSONObject): OnlineTrack? {
        try {
            // Extract videoId
            val videoId = item.optJSONObject("playlistItemData")?.optString("videoId")
                ?.takeIf { it.isNotBlank() }
                ?: item.optJSONArray("flexColumns")?.optJSONObject(0)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)
                    ?.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
                    ?.optString("videoId")
                ?: return null

            // Extract Title from column 0
            val flexCols = item.optJSONArray("flexColumns") ?: return null
            val col0 = flexCols.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            val title = col0?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Unknown Track"

            // Extract Artist, Album, and Duration from column 1
            var artist = "YouTube Music"
            var album: String? = null
            var durationMs = 0L

            val col1 = flexCols.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            val runs = col1?.optJSONObject("text")?.optJSONArray("runs")
            if (runs != null && runs.length() > 0) {
                val artistCandidate = runs.optJSONObject(0)?.optString("text", "")
                if (!artistCandidate.isNullOrBlank()) {
                    artist = artistCandidate
                }

                for (r in 1 until runs.length()) {
                    val runText = runs.optJSONObject(r)?.optString("text", "") ?: continue
                    if (runText.contains(":") && runText.length <= 8) {
                        durationMs = parseDuration(runText)
                    } else if (runText.length > 2 && runText != "•" && album == null) {
                        album = runText
                    }
                }
            }

            // Extract Thumbnail
            var thumbnailUrl: String? = null
            val thumbnails = item.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")

            if (thumbnails != null && thumbnails.length() > 0) {
                thumbnailUrl = thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url")
            }

            return OnlineTrack(
                videoId = videoId,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                thumbnailUrl = thumbnailUrl
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseDuration(durationStr: String): Long {
        return try {
            val parts = durationStr.split(":")
            if (parts.size == 2) {
                val min = parts[0].toLong()
                val sec = parts[1].toLong()
                (min * 60 + sec) * 1000L
            } else if (parts.size == 3) {
                val hr = parts[0].toLong()
                val min = parts[1].toLong()
                val sec = parts[2].toLong()
                (hr * 3600 + min * 60 + sec) * 1000L
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun getPlayerResponse(
        videoId: String,
        profile: YouTubeClientProfile
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val visitorData = getVisitorData()
            val clientJson = JSONObject().apply {
                put("clientName", profile.clientName)
                put("clientVersion", profile.clientVersion)
                profile.clientId.let { put("clientId", it) }
                profile.osName?.let { put("osName", it) }
                profile.osVersion?.let { put("osVersion", it) }
                profile.deviceMake?.let { put("deviceMake", it) }
                profile.deviceModel?.let { put("deviceModel", it) }
                profile.androidSdkVersion?.let { put("androidSdkVersion", it) }
                put("hl", "en")
                put("gl", "US")
                if (!visitorData.isNullOrBlank()) {
                    put("visitorData", visitorData)
                }
            }

            val requestBodyJson = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", clientJson)
                })
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        put("html5Preference", "HTML5_PREF_WANTS")
                    })
                })
            }

            val requestBuilder = Request.Builder()
                .url(profile.playerEndpointUrl)
                .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", profile.userAgent)
                .header("X-YouTube-Client-Name", profile.clientId)
                .header("X-YouTube-Client-Version", profile.clientVersion)

            if (!visitorData.isNullOrBlank()) {
                requestBuilder.header("X-Goog-Visitor-Id", visitorData)
            }

            val request = requestBuilder.build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Player endpoint HTTP ${response.code} with profile ${profile.name}"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty player body"))
                Result.success(JSONObject(body))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
