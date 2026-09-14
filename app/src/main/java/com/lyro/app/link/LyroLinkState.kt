package com.lyro.app.link

/**
 * High-level status of the Lyro Link local server and service.
 */
enum class LyroLinkStatus {
    OFF,
    STARTING,
    RUNNING,
    ERROR
}

/**
 * State of the Lyro Link local Wi-Fi music streaming server.
 */
data class LyroLinkState(
    val status: LyroLinkStatus = LyroLinkStatus.OFF,
    val enabled: Boolean = false,
    val running: Boolean = false,
    val localIp: String? = null,
    val port: Int? = null,
    val connectedClients: Int = 0,
    val pairingCode: String? = null,
    val statusMessage: String? = null
) {
    /**
     * Resolves the full URL to open on laptop/tablet browser.
     * Example: "http://192.168.1.42:8765"
     */
    val fullAddress: String?
        get() = if (localIp != null && port != null) "http://$localIp:$port" else null
}
