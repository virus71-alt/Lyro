package com.lyro.app.streaming

import android.util.Log
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
    private const val TAG = "LyroFormatSelector"

    fun selectBestAudioFormat(
        adaptiveFormatsJson: JSONArray,
        quality: AudioQuality = AudioQuality.AUTO
    ): FormatCandidate? {
        val candidates = mutableListOf<FormatCandidate>()
        var audioDirectUrls = 0
        var audioSignatureCipher = 0
        var audioBare = 0

        for (i in 0 until adaptiveFormatsJson.length()) {
            val format = adaptiveFormatsJson.optJSONObject(i) ?: continue
            val mimeType = format.optString("mimeType", "")
            if (!mimeType.startsWith("audio/")) continue

            val directUrl = format.optString("url", "")
            val cipher = format.optString("signatureCipher", "").ifEmpty { format.optString("cipher", "") }

            when {
                directUrl.isNotBlank() -> audioDirectUrls++
                cipher.isNotBlank() -> audioSignatureCipher++
                else -> audioBare++
            }

            if (directUrl.isBlank()) continue

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

        Log.d(TAG, "Format inspection: audioDirectUrls=$audioDirectUrls, audioSignatureCipher=$audioSignatureCipher, audioBare=$audioBare")

        if (candidates.isEmpty()) {
            Log.w(TAG, "No direct playable audio URL found from format candidates (ciphers=$audioSignatureCipher, bare=$audioBare)")
            return null
        }

        return when (quality) {
            AudioQuality.HIGH -> {
                candidates.maxByOrNull { it.bitrate }
            }
            AudioQuality.LOW -> {
                candidates.minByOrNull { it.bitrate }
            }
            AudioQuality.AUTO -> {
                // Prioritize itag 140 (m4a/AAC ~128kbps) for standard Media3 ExoPlayer compatibility,
                // followed by itag 251 (Opus ~160kbps)
                candidates.find { it.itag == 140 }
                    ?: candidates.find { it.itag == 251 }
                    ?: candidates.maxByOrNull { it.bitrate }
            }
        }
    }
}
