package com.lyro.app.link

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * High-level coordinator for Lyro Link.
 * Manages server lifecycle, local IP detection, foreground service,
 * and state exposure via StateFlow<LyroLinkState>.
 */
class LyroLinkManager(
    private val context: Context
) {

    companion object {
        private const val TAG = "LyroLinkManager"
        const val DEFAULT_PORT = 8765
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    val auth = LyroLinkAuth()
    val libraryProvider = LyroLinkLibraryProvider(context)
    val streamProvider = LyroLinkStreamProvider(context, libraryProvider)
    val webAssets = LyroLinkWebAssets(context)
    val routes = LyroLinkRoutes(context, auth, libraryProvider, streamProvider, webAssets)

    private var server: LyroLinkServer? = null

    private val _state = MutableStateFlow(LyroLinkState())
    val state: StateFlow<LyroLinkState> = _state.asStateFlow()

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handleNetworkChange()
        }

        override fun onLost(network: Network) {
            handleNetworkChange()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            handleNetworkChange()
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    /**
     * Turns Lyro Link ON and starts the embedded HTTP server.
     */
    @Synchronized
    fun start() {
        if (_state.value.running) return

        val localIp = resolveLocalWifiIp()
        if (localIp == null) {
            Log.w(TAG, "Cannot start Lyro Link: No active local Wi-Fi IPv4 found")
            _state.value = LyroLinkState(
                enabled = true,
                running = false,
                statusMessage = "Connect to Wi-Fi to use Lyro Link"
            )
            return
        }

        try {
            val newServer = LyroLinkServer(DEFAULT_PORT, routes)
            val actualPort = newServer.startSafe()
            server = newServer

            val code = auth.getPairingCode()

            _state.value = LyroLinkState(
                enabled = true,
                running = true,
                localIp = localIp,
                port = actualPort,
                pairingCode = code,
                connectedClients = auth.getConnectedClientsCount(),
                statusMessage = "Ready for connections"
            )

            // Start foreground service
            LyroLinkService.start(context)

            // Start periodic client count & status refresher
            startMonitoring()
            Log.i(TAG, "Lyro Link active at http://$localIp:$actualPort with pairing code $code")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Lyro Link server: ${e.message}", e)
            _state.value = LyroLinkState(
                enabled = true,
                running = false,
                statusMessage = "Failed to start: ${e.message}"
            )
        }
    }

    /**
     * Turns Lyro Link OFF, shuts down server, and cleans up active sessions.
     */
    @Synchronized
    fun stop() {
        monitorJob?.cancel()
        monitorJob = null

        try {
            server?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping server: ${e.message}")
        }
        server = null

        auth.invalidateAllSessions()
        LyroLinkService.stop(context)

        _state.value = LyroLinkState(
            enabled = false,
            running = false,
            localIp = null,
            port = null,
            pairingCode = null,
            connectedClients = 0,
            statusMessage = "Lyro Link stopped"
        )
        Log.i(TAG, "Lyro Link has been stopped")
    }

    /**
     * Regenerates a new 6-digit pairing code while server is running.
     */
    fun regeneratePairingCode() {
        val newCode = auth.regeneratePairingCode()
        _state.value = _state.value.copy(pairingCode = newCode)
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                delay(3000)
                if (!_state.value.running) break

                val clients = auth.getConnectedClientsCount()
                if (clients != _state.value.connectedClients) {
                    _state.value = _state.value.copy(connectedClients = clients)
                }
            }
        }
    }

    private fun handleNetworkChange() {
        if (!_state.value.enabled) return

        scope.launch {
            val currentIp = resolveLocalWifiIp()
            if (currentIp == null && _state.value.running) {
                // Wi-Fi lost
                _state.value = _state.value.copy(
                    running = false,
                    statusMessage = "Wi-Fi disconnected"
                )
                server?.stop()
                server = null
            } else if (currentIp != null && (!_state.value.running || currentIp != _state.value.localIp)) {
                // Wi-Fi restored or new IP assigned
                stop()
                start()
            }
        }
    }

    /**
     * Resolves the device's local LAN IPv4 address on active Wi-Fi / Ethernet interface.
     */
    fun resolveLocalWifiIp(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

            // Priority 1: wlan, ap, eth interfaces
            val preferredInterfaces = interfaces.filter { iface ->
                !iface.isLoopback && iface.isUp &&
                        (iface.name.startsWith("wlan", ignoreCase = true) ||
                         iface.name.startsWith("ap", ignoreCase = true) ||
                         iface.name.startsWith("eth", ignoreCase = true))
            }

            for (iface in preferredInterfaces) {
                val ip = extractValidIpv4(iface)
                if (ip != null) return ip
            }

            // Priority 2: any active non-loopback interface
            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                val ip = extractValidIpv4(iface)
                if (ip != null) return ip
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating network interfaces: ${e.message}")
        }
        return null
    }

    private fun extractValidIpv4(iface: NetworkInterface): String? {
        val addrs = Collections.list(iface.inetAddresses)
        for (addr in addrs) {
            if (!addr.isLoopbackAddress && addr is Inet4Address) {
                val ip = addr.hostAddress ?: continue
                if (!ip.startsWith("127.") && !ip.startsWith("169.254.")) {
                    return ip
                }
            }
        }
        return null
    }
}
