package com.lyro.app.streaming

interface StreamResolver {
    suspend fun resolve(
        videoId: String,
        quality: AudioQuality = AudioQuality.AUTO,
        excludeProfiles: Set<String> = emptySet()
    ): Result<ResolvedStream>
}
