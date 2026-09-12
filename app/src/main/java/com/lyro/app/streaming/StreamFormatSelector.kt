package com.lyro.app.streaming

import org.json.JSONArray
import org.json.JSONObject

data class FormatCandidate(
    val itag: Int,
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val contentLength: Long?
)

object StreamFormatSelector {

    fun selectBestAudioFormat(
        adaptiveFormatsJson: JSONArray,
        quality: AudioQuality = AudioQuality.AUTO
    ): FormatCandidate? {
        val candidates = mutableListOf<FormatCandidate>()

        for (i in 0 until adaptiveFormatsJson.length()) {
            val format = adaptiveFormatsJson.optJSONObject(i) ?: continue
            val mimeType = format.optString("mimeType", "")
            if (!mimeType.startsWith("audio/")) continue

            val directUrl = format.optString("url", "")
            if (directUrl.isBlank()) continue // Skip cipher-encrypted formats for now

            val itag = format.optInt("itag", 0)
            val bitrate = format.optInt("bitrate", 0)
            val contentLength = format.optString("contentLength", "").toLongOrNull()

            candidates.add(
                FormatCandidate(
                    itag = itag,
                    url = directUrl,
                    mimeType = mimeType,
                    bitrate = bitrate,
                    contentLength = contentLength
                )
            )
        }

        if (candidates.isEmpty()) return null

        return when (quality) {
            AudioQuality.HIGH -> {
                candidates.maxByOrNull { it.bitrate }
            }
            AudioQuality.LOW -> {
                candidates.minByOrNull { it.bitrate }
            }
            AudioQuality.AUTO -> {
                // Prioritize itag 140 (m4a/AAC ~128kbps) for rock-solid Android Media3 compatibility,
                // followed by itag 251 (Opus ~160kbps)
                candidates.find { it.itag == 140 }
                    ?: candidates.find { it.itag == 251 }
                    ?: candidates.maxByOrNull { it.bitrate }
            }
        }
    }
}
