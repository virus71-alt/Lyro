package com.lyro.app.core.artwork

/**
 * Utility for resolving and upgrading artwork URLs to their highest available resolution.
 */
object ArtworkUtils {

    /**
     * Converts a thumbnail URL to its highest-resolution equivalent.
     *
     * 1. Google User Content / YouTube Music CDN (lh3.googleusercontent.com, yt3.ggpht.com):
     *    YouTube Music sends query parameters like `=w60-h60-l90-rj` or `=s120` to downscale
     *    for search lists. Modifying the dimension parameter to `=w1200-h1200-l90-rj`
     *    requests the full-size uncompressed master album art directly from Google's CDN.
     *
     * 2. YouTube Video Thumbnails (i.ytimg.com, img.youtube.com):
     *    Upgrades standard 480x360 / 120x90 thumbnails (/hqdefault.jpg, /default.jpg, etc.)
     *    to /maxresdefault.jpg (1280x720) with graceful fallback.
     */
    fun getHighResArtworkUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null

        // 1. Google User Content (YouTube Music album artwork)
        if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
            return when {
                url.contains(Regex("=w\\d+-h\\d+")) -> {
                    url.replace(Regex("=w\\d+-h\\d+[^?]*"), "=w1200-h1200-l90-rj")
                }
                url.contains(Regex("=s\\d+")) -> {
                    url.replace(Regex("=s\\d+[^?]*"), "=s1200")
                }
                url.contains("=") -> {
                    val base = url.substringBeforeLast("=")
                    "$base=w1200-h1200-l90-rj"
                }
                else -> {
                    "$url=w1200-h1200-l90-rj"
                }
            }
        }

        // 2. YouTube Video Thumbnails (i.ytimg.com)
        if (url.contains("i.ytimg.com") || url.contains("img.youtube.com")) {
            if (url.contains(Regex("/(default|mqdefault|hqdefault|sddefault)\\.jpg"))) {
                return url.replace(Regex("/(default|mqdefault|hqdefault|sddefault)\\.jpg"), "/maxresdefault.jpg")
            }
        }

        return url
    }

    /**
     * Fallback URL for video thumbnails if maxresdefault.jpg is not available.
     */
    fun getFallbackArtworkUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        if (url.contains("i.ytimg.com") || url.contains("img.youtube.com")) {
            if (url.contains("maxresdefault.jpg")) {
                return url.replace("maxresdefault.jpg", "hqdefault.jpg")
            }
        }
        return url
    }
}
