package com.lyro.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface NetworkMonitor {
    val networkStatus: StateFlow<NetworkStatus>
    val isOnline: StateFlow<Boolean>
}

class ConnectivityNetworkMonitor(
    context: Context
) : NetworkMonitor {

    companion object {
        private const val TAG = "LyroNetworkMonitor"
    }

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _networkStatus = MutableStateFlow(NetworkStatus.OFFLINE)
    override val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    // Track active valid networks
    private val validNetworks = mutableSetOf<Network>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val capabilities = connectivityManager?.getNetworkCapabilities(network)
            val isValidated = checkCapabilities(capabilities)
            synchronized(validNetworks) {
                if (isValidated) {
                    validNetworks.add(network)
                }
                updateStatus()
            }
            Log.d(TAG, "Network onAvailable: $network, isValidated=$isValidated, totalValid=${validNetworks.size}")
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val isValidated = checkCapabilities(networkCapabilities)
            synchronized(validNetworks) {
                if (isValidated) {
                    validNetworks.add(network)
                } else {
                    validNetworks.remove(network)
                }
                updateStatus()
            }
            Log.d(TAG, "Network onCapabilitiesChanged: $network, isValidated=$isValidated, totalValid=${validNetworks.size}")
        }

        override fun onLost(network: Network) {
            synchronized(validNetworks) {
                validNetworks.remove(network)
                updateStatus()
            }
            Log.d(TAG, "Network onLost: $network, totalValid=${validNetworks.size}")
        }

        override fun onUnavailable() {
            synchronized(validNetworks) {
                validNetworks.clear()
                updateStatus()
            }
            Log.d(TAG, "Network onUnavailable")
        }
    }

    init {
        // Initial evaluation from active network
        evaluateInitialConnectivity()

        // Register real-time callback
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}", e)
        }
    }

    private fun evaluateInitialConnectivity() {
        val cm = connectivityManager ?: return
        val activeNet = cm.activeNetwork
        val caps = if (activeNet != null) cm.getNetworkCapabilities(activeNet) else null
        val isInitialOnline = checkCapabilities(caps)

        synchronized(validNetworks) {
            if (isInitialOnline && activeNet != null) {
                validNetworks.add(activeNet)
            }
            _networkStatus.value = if (isInitialOnline) NetworkStatus.ONLINE else NetworkStatus.OFFLINE
            _isOnline.value = isInitialOnline
        }
        Log.i(TAG, "Initial network evaluation: online=$isInitialOnline")
    }

    private fun checkCapabilities(caps: NetworkCapabilities?): Boolean {
        if (caps == null) return false
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!hasInternet) return false

        // Check validation if API level >= 23
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            true
        }
    }

    private fun updateStatus() {
        val online = validNetworks.isNotEmpty()
        val newStatus = if (online) NetworkStatus.ONLINE else NetworkStatus.OFFLINE
        if (_networkStatus.value != newStatus) {
            _networkStatus.value = newStatus
            _isOnline.value = online
            Log.i(TAG, "Network status changed -> $newStatus (isOnline=$online)")
        }
    }
}
