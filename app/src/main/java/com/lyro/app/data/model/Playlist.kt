package com.lyro.app.data.model

data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int = 0,
    val colorIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
