package com.lyro.app.data.repository

import com.lyro.app.data.model.OnlineTrack
import com.lyro.app.data.remote.youtube.InnertubeClient

open class OnlineMusicRepository {

    open suspend fun searchSongs(query: String): Result<List<OnlineTrack>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return Result.success(emptyList())
        }
        return InnertubeClient.searchSongs(trimmed)
    }
}
