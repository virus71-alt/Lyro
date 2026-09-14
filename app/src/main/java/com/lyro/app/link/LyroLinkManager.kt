package com.lyro.app.link

import android.content.Context
import android.content.Intent
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
 * Manages server lifecycle, transactional foreground service startup,
 * local IP detection, and clean non-recursive stop handling.
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
     * Starts Lyro Link transactionally:
     * 1. Resolves local Wi-Fi IP.
     * 2. Sets status to STARTING.
     * 3. Starts embedded HTTP server.
     * 4. Starts foreground service.
     * Only when foreground promotion succeeds will the state become RUNNING.
     */
    @Synchronized
    fun start() {
        if (_state.value.status == LyroLinkStatus.STARTING || _state.value.status == LyroLinkStatus.RUNNING) {
            Log.d(TAG, "Lyro Link already starting or running, ignoring start request")
            return
        }

        Log.d(TAG, "Resolving IP...")
        val localIp = resolveLocalWifiIp()
        if (localIp == null) {
            Log.w(TAG, "Cannot start Lyro Link: No active local Wi-Fi IPv4 found")
            _state.value = LyroLinkState(
                status = LyroLinkStatus.ERROR,
                enabled = false,
                running = false,
                statusMessage = "Connect to Wi-Fi to use Lyro Link"
            )
            return
        }
        Log.d(TAG, "Resolved IP: $localIp")

        _state.value = LyroLinkState(
            status = LyroLinkStatus.STARTING,
            enabled = true,
            running = false,
            localIp = localIp,
            statusMessage = "Starting HTTP server..."
        )

        try {
            Log.d(TAG, "Starting HTTP server...")
            val newServer = LyroLinkServer(DEFAULT_PORT, routes)
            val actualPort = newServer.startSafe()
            server = newServer
            Log.i(TAG, "HTTP server started on port $actualPort")

            val code = auth.getPairingCode()

            _state.value = _state.value.copy(
                port = actualPort,
                pairingCode = code,
                statusMessage = "Starting background service..."
            )

            // Start foreground service defensively
            Log.d(TAG, "Starting foreground service...")
            try {
                LyroLinkService.start(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request foreground service start: ${e.message}", e)
                onForegroundServiceFailed("Could not start background service: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Lyro Link server: ${e.message}", e)
            stopServerOnly()
            _state.value = LyroLinkState(
                status = LyroLinkStatus.ERROR,
                enabled = false,
                running = false,
                statusMessage = "Failed to start server: ${e.message}"
            )
        }
    }

    /**
     * Called by LyroLinkService when startForeground() succeeds.
     * Completes the startup transaction and publishes the stable RUNNING state.
     */
    @Synchronized
    fun onForegroundServiceStarted() {
        if (!_state.value.enabled) return

        _state.value = _state.value.copy(
            status = LyroLinkStatus.RUNNING,
            running = true,
            statusMessage = "Ready for connections",
            connectedClients = auth.getConnectedClientsCount()
        )

        startMonitoring()
        Log.i(TAG, "Lyro Link active at http://${_state.value.localIp}:${_state.value.port}")
    }

    /**
     * Called by LyroLinkService when foreground promotion fails.
     * Cleans up server resources and publishes the ERROR state without crashing the app.
     */
    @Synchronized
    fun onForegroundServiceFailed(reason: String) {
        Log.e(TAG, "Foreground service promotion failed: $reason")
        stopServerOnly()
        _state.value = LyroLinkState(
            status = LyroLinkStatus.ERROR,
            enabled = false,
            running = false,
            statusMessage = "Lyro Link couldn't start on this device: $reason"
        )
    }

    /**
     * Stops the server, invalidates sessions, and cancels monitoring jobs
     * without calling stopService (avoids circular stop recursion).
     */
    @Synchronized
    fun stopServerOnly() {
        monitorJob?.cancel()
        monitorJob = null

        try {
            server?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping server: ${e.message}")
        }
        server = null

        auth.invalidateAllSessions()
    }

    /**
     * Fully terminates Lyro Link from the UI.
     * Cleans up server and stops foreground service cleanly.
     */
    @Synchronized
    fun stop() {
        stopServerOnly()

        try {
            context.stopService(Intent(context, LyroLinkService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping LyroLinkService: ${e.message}")
        }

        _state.value = LyroLinkState(
            status = LyroLinkStatus.OFF,
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
                    status = LyroLinkStatus.ERROR,
                    running = false,
                    statusMessage = "Wi-Fi disconnected"
                )
                stopServerOnly()
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
