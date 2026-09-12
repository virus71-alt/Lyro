package com.lyro.app.streaming

data class ResolvedStream(
    val url: String,
    val mimeType: String?,
    val bitrate: Int?,
    val contentLength: Long?,
    val expiresAtEpochSeconds: Long? = null,
    val itag: Int? = null,
    val clientProfileName: String? = null
)
