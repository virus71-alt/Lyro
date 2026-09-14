package com.lyro.app.core.smartdownload

import android.content.Context
import android.os.StatFs
import android.util.Log
import androidx.work.*
import com.lyro.app.data.download.DownloadOrigin
import com.lyro.app.data.download.MusicDownloader
import com.lyro.app.data.local.LyroDatabaseHelper
import com.lyro.app.data.preferences.SmartDownloadPreferences
import com.lyro.app.service.PlaybackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Central coordinator for Lyro Smart Offline Mix.
 * Manages quota enforcement, device storage safety buffers, candidate selection,
 * sequential downloads, rotation, and WorkManager background task scheduling.
 */
class SmartDownloadManager(
    private val context: Context,
    val preferences: SmartDownloadPreferences,
    val candidateProvider: SmartDownloadCandidateProvider,
    val retentionEvaluator: SmartDownloadRetentionEvaluator,
    val downloader: MusicDownloader,
    val dbHelper: LyroDatabaseHelper,
    val playbackManager: PlaybackManager
) {
    companion object {
        private const val TAG = "LyroSmartDownloadMgr"
        const val WORK_NAME_PERIODIC = "LYRO_SMART_DOWNLOAD_PERIODIC"
        const val WORK_NAME_ONCE = "LYRO_SMART_DOWNLOAD_NOW"

        // Default safety buffer: 500 MB or 5% of device storage
        const val MIN_FREE_STORAGE_BUFFER_BYTES = 500L * 1024 * 1024
    }

    private val _state = MutableStateFlow(computeInitialState())
    val state: StateFlow<SmartDownloadsState> = _state.asStateFlow()

    private fun computeInitialState(): SmartDownloadsState {
        val used = dbHelper.getSmartDownloadsTotalBytes()
        val count = dbHelper.getSmartDownloadedMetadata().size
        return SmartDownloadsState(
            isEnabled = preferences.isEnabled.value,
            limitBytes = preferences.storageLimitBytes.value,
            usedBytes = used,
            trackCount = count,
            isMaintaining = false,
            lastUpdated = preferences.lastMaintenanceTimestamp.value,
            lastError = preferences.lastMaintenanceError.value
        )
    }

    fun refreshState() {
        val used = dbHelper.getSmartDownloadsTotalBytes()
        val count = dbHelper.getSmartDownloadedMetadata().size
        _state.value = _state.value.copy(
            isEnabled = preferences.isEnabled.value,
            limitBytes = preferences.storageLimitBytes.value,
            usedBytes = used,
            trackCount = count,
            lastUpdated = preferences.lastMaintenanceTimestamp.value,
            lastError = preferences.lastMaintenanceError.value
        )
    }

    /**
     * Checks if the device has sufficient free storage above the safety buffer.
     * Prevents filling the phone to capacity.
     */
    fun hasStorageSafetyBuffer(): Boolean {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            val available = stat.availableBytes
            val total = stat.totalBytes
            val requiredBuffer = maxOf(MIN_FREE_STORAGE_BUFFER_BYTES, (total * 0.05).toLong())
            available >= requiredBuffer
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read statFs for storage safety: ${e.message}")
            true
        }
    }

    /**
     * Executes the Smart Download maintenance cycle.
     */
    suspend fun executeMaintenance(isManualRefresh: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        if (!preferences.isEnabled.value && !isManualRefresh) {
            Log.d(TAG, "Smart Downloads disabled. Skipping maintenance.")
            return@withContext Result.success(Unit)
        }

        _state.value = _state.value.copy(isMaintaining = true)
        Log.i(TAG, "Starting Smart Downloads maintenance cycle (isManual=$isManualRefresh)...")

        try {
            // 1. Device storage safety check
            if (!hasStorageSafetyBuffer()) {
                val err = "Device storage low. Preserving safety buffer; halting smart downloads."
                Log.w(TAG, err)
                preferences.recordMaintenanceError(err)
                _state.value = _state.value.copy(isMaintaining = false, lastError = err)
                return@withContext Result.failure(Exception(err))
            }

            // 2. Clean expired cooldowns (older than 30 days)
            dbHelper.cleanExpiredCooldowns()

            // 3. Inspect quota
            val quotaLimit = preferences.storageLimitBytes.value
            var currentUsed = dbHelper.getSmartDownloadsTotalBytes()

            // Active playback identifiers for safety check
            val currentTrack = playbackManager.currentTrack.value
            val currentPlayingId = currentTrack?.onlineVideoId ?: currentTrack?.id
            val activeQueueIds = playbackManager.queue.value.mapNotNull { it.onlineVideoId ?: it.id }.toSet()

            // 4. Generate recommendations
            val candidates = candidateProvider.getCandidates(
                targetCount = 40,
                activeTrackIds = activeQueueIds + setOfNotNull(currentPlayingId)
            )

            if (candidates.isEmpty()) {
                Log.i(TAG, "No candidate tracks available for smart download.")
                preferences.recordMaintenanceCompleted()
                _state.value = _state.value.copy(isMaintaining = false)
                return@withContext Result.success(Unit)
            }

            val profile = candidateProvider.let {
                com.lyro.app.LyroApplication.instance.tasteProfileRepository.tasteProfile.value
            }

            // 5. Sequential conservative download
            for (candidate in candidates) {
                if (!hasStorageSafetyBuffer()) {
                    Log.w(TAG, "Safety storage threshold reached during download loop. Stopping.")
                    break
                }

                val estimatedBytes = when {
                    candidate.track.durationMs > 0 -> (candidate.track.durationMs / 1000L) * 16_000L // ~128kbps estimate
                    else -> 3_500_000L // 3.5 MB average
                }

                // If quota would be exceeded, evaluate rotation
                if (currentUsed + estimatedBytes > quotaLimit) {
                    val existingSmart = dbHelper.getSmartDownloadedMetadata()
                    val evictableCandidates = existingSmart.filter {
                        retentionEvaluator.isSafeToEvict(it, currentPlayingId, activeQueueIds)
                    }

                    if (evictableCandidates.isNotEmpty()) {
                        // Score existing tracks
                        val scoredEvictable = evictableCandidates.map { meta ->
                            val isLiked = dbHelper.isUnifiedFavorite(meta.videoId)
                            val retScore = retentionEvaluator.calculateRetentionScore(meta, profile, isLiked)
                            meta to retScore
                        }.sortedBy { it.second } // Lowest retention score first

                        val worstExisting = scoredEvictable.firstOrNull()
                        if (worstExisting != null && retentionEvaluator.shouldReplace(candidate.score, worstExisting.second)) {
                            Log.i(TAG, "Rotating out smart download: ${worstExisting.first.title} (retention=${worstExisting.second}) in favor of candidate: ${candidate.track.title} (score=${candidate.score})")
                            downloader.deleteDownload(worstExisting.first.videoId)
                            dbHelper.addToSmartCooldown(worstExisting.first.videoId, "ROTATED_FOR_HIGHER_AFFINITY")
                            currentUsed = dbHelper.getSmartDownloadsTotalBytes()
                        } else {
                            Log.i(TAG, "Quota limit reached and new candidates do not exceed hysteresis threshold. Stopping download cycle.")
                            break
                        }
                    } else {
                        Log.i(TAG, "Quota limit reached and no tracks are safe to evict. Stopping download cycle.")
                        break
                    }
                }

                // Download candidate
                Log.d(TAG, "Smart downloading: ${candidate.track.title} by ${candidate.track.artist} (score=${candidate.score})")
                val result = downloader.downloadTrack(
                    track = candidate.track,
                    origin = DownloadOrigin.SMART,
                    score = candidate.score
                )

                if (result.isSuccess) {
                    currentUsed = dbHelper.getSmartDownloadsTotalBytes()
                    refreshState()
                    delay(300) // Keep device quiet and responsive
                } else {
                    Log.w(TAG, "Failed to download candidate ${candidate.track.title}: ${result.exceptionOrNull()?.message}")
                }
            }

            preferences.recordMaintenanceCompleted()
            refreshState()
            _state.value = _state.value.copy(isMaintaining = false)
            Log.i(TAG, "Smart Downloads maintenance cycle completed successfully.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error in Smart Downloads maintenance: ${e.message}", e)
            preferences.recordMaintenanceError(e.message ?: "Unknown maintenance error")
            refreshState()
            _state.value = _state.value.copy(isMaintaining = false)
            Result.failure(e)
        }
    }

    /**
     * Promotes a smart download to manual ownership when the user taps Download.
     */
    fun promoteToManual(videoId: String) {
        dbHelper.updateDownloadOrigin(videoId, DownloadOrigin.MANUAL)
        refreshState()
    }

    /**
     * Removes all smart downloads, preserving manual downloads and local MediaStore tracks.
     */
    suspend fun removeSmartDownloads(): Int = withContext(Dispatchers.IO) {
        val deleted = downloader.deleteSmartDownloadsOnly()
        refreshState()
        deleted
    }

    /**
     * Triggers a manual one-off refresh of the offline mix.
     */
    fun refreshOfflineMix() {
        val constraints = buildWorkConstraints()
        val request = OneTimeWorkRequestBuilder<SmartDownloadWorker>()
            .setConstraints(constraints)
            .addTag("SMART_DOWNLOAD_NOW")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME_ONCE, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Schedules periodic 12-24 hour maintenance.
     */
    fun schedulePeriodicWork() {
        if (!preferences.isEnabled.value) return

        val constraints = buildWorkConstraints()
        val request = PeriodicWorkRequestBuilder<SmartDownloadWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .addTag("SMART_DOWNLOAD_PERIODIC")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        Log.d(TAG, "Scheduled periodic Smart Downloads work (12 hours).")
    }

    /**
     * Cancels any scheduled Smart Download background tasks.
     */
    fun cancelWork() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_ONCE)
        Log.d(TAG, "Cancelled Smart Downloads background tasks.")
    }

    private fun buildWorkConstraints(): Constraints {
        val isWifiOnly = preferences.isWifiOnly.value
        val isChargingOnly = preferences.isChargingOnly.value

        return Constraints.Builder()
            .setRequiredNetworkType(if (isWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .apply {
                if (isChargingOnly) setRequiresCharging(true)
            }
            .build()
    }
}
