package com.lyro.app.core.smartdownload

data class SmartDownloadsState(
    val isEnabled: Boolean = false,
    val limitBytes: Long = 2L * 1024 * 1024 * 1024,
    val usedBytes: Long = 0L,
    val trackCount: Int = 0,
    val isMaintaining: Boolean = false,
    val lastUpdated: Long = 0L,
    val lastError: String? = null
)
