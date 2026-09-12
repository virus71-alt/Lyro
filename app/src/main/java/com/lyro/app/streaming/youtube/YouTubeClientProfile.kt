package com.lyro.app.streaming.youtube

data class YouTubeClientProfile(
    val name: String,
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: Int? = null,
    val playerEndpointUrl: String = "https://www.youtube.com/youtubei/v1/player"
) {
    companion object {
        /**
         * ANDROID_VR pin matching yt-dlp & YouTube.js.
         * Proven to serve direct googlevideo audio streams (itag 140 / 251) without cipher deciphering.
         */
        val ANDROID_VR = YouTubeClientProfile(
            name = "ANDROID_VR",
            clientName = "ANDROID_VR",
            clientVersion = "1.65.10",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            osName = "Android",
            osVersion = "12L",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = 32
        )

        /**
         * VISIONOS client profile.
         * Tested and verified to return 200 OK with direct audio URLs and no 1MB playback cap.
         */
        val VISIONOS = YouTubeClientProfile(
            name = "VISIONOS",
            clientName = "VISIONOS",
            clientVersion = "0.1",
            clientId = "101",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
            osName = "visionOS",
            osVersion = "1.3.21O771",
            deviceMake = "Apple",
            deviceModel = "RealityDevice14,1"
        )

        val ALL_PROFILES = listOf(ANDROID_VR, VISIONOS)
    }
}
