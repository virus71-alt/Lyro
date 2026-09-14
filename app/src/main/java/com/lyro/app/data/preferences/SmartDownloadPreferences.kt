package com.lyro.app.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SmartDownloadPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "lyro_smart_downloads_prefs"
        private const val KEY_ENABLED = "smart_downloads_enabled"
        private const val KEY_STORAGE_LIMIT_BYTES = "storage_limit_bytes"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_CHARGING_ONLY = "charging_only"
        private const val KEY_HAS_SEEN_CONSENT = "has_seen_consent"
        private const val KEY_LAST_MAINTENANCE = "last_maintenance_timestamp"
        private const val KEY_LAST_ERROR = "last_maintenance_error"

        const val ONE_GB_BYTES = 1L * 1024 * 1024 * 1024
        const val TWO_GB_BYTES = 2L * 1024 * 1024 * 1024
        const val THREE_GB_BYTES = 3L * 1024 * 1024 * 1024
        const val FIVE_GB_BYTES = 5L * 1024 * 1024 * 1024

        val PRESET_OPTIONS = listOf(ONE_GB_BYTES, TWO_GB_BYTES, THREE_GB_BYTES, FIVE_GB_BYTES)
    }

    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _isEnabled.value = enabled
    }

    private val _storageLimitBytes = MutableStateFlow(prefs.getLong(KEY_STORAGE_LIMIT_BYTES, TWO_GB_BYTES))
    val storageLimitBytes: StateFlow<Long> = _storageLimitBytes.asStateFlow()

    fun setStorageLimitBytes(bytes: Long) {
        prefs.edit().putLong(KEY_STORAGE_LIMIT_BYTES, bytes).apply()
        _storageLimitBytes.value = bytes
    }

    private val _isWifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, true))
    val isWifiOnly: StateFlow<Boolean> = _isWifiOnly.asStateFlow()

    fun setWifiOnly(wifiOnly: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply()
        _isWifiOnly.value = wifiOnly
    }

    private val _isChargingOnly = MutableStateFlow(prefs.getBoolean(KEY_CHARGING_ONLY, false))
    val isChargingOnly: StateFlow<Boolean> = _isChargingOnly.asStateFlow()

    fun setChargingOnly(chargingOnly: Boolean) {
        prefs.edit().putBoolean(KEY_CHARGING_ONLY, chargingOnly).apply()
        _isChargingOnly.value = chargingOnly
    }

    private val _hasSeenConsentDialog = MutableStateFlow(prefs.getBoolean(KEY_HAS_SEEN_CONSENT, false))
    val hasSeenConsentDialog: StateFlow<Boolean> = _hasSeenConsentDialog.asStateFlow()

    fun setHasSeenConsentDialog(seen: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SEEN_CONSENT, seen).apply()
        _hasSeenConsentDialog.value = seen
    }

    private val _lastMaintenanceTimestamp = MutableStateFlow(prefs.getLong(KEY_LAST_MAINTENANCE, 0L))
    val lastMaintenanceTimestamp: StateFlow<Long> = _lastMaintenanceTimestamp.asStateFlow()

    fun recordMaintenanceCompleted() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_MAINTENANCE, now).remove(KEY_LAST_ERROR).apply()
        _lastMaintenanceTimestamp.value = now
        _lastMaintenanceError.value = null
    }

    private val _lastMaintenanceError = MutableStateFlow(prefs.getString(KEY_LAST_ERROR, null))
    val lastMaintenanceError: StateFlow<String?> = _lastMaintenanceError.asStateFlow()

    fun recordMaintenanceError(error: String) {
        prefs.edit().putString(KEY_LAST_ERROR, error).apply()
        _lastMaintenanceError.value = error
    }
}
