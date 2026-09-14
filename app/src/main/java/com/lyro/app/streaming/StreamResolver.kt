package com.lyro.app.streaming

class OfflineException(message: String = "Device is offline and cannot stream audio") : Exception(message)

interface StreamResolver {
    suspend fun resolve(
        videoId: String,
        quality: AudioQuality = AudioQuality.AUTO,
        excludeProfiles: Set<String> = emptySet()
    ): Result<ResolvedStream>
}

