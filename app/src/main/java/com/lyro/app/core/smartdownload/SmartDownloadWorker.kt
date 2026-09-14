package com.lyro.app.core.smartdownload

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lyro.app.LyroApplication

/**
 * Android WorkManager CoroutineWorker that executes Smart Downloads maintenance
 * strictly when battery, storage, and network constraints are satisfied.
 */
class SmartDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SmartDownloadWorker"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "SmartDownloadWorker started execution.")
        val manager = try {
            LyroApplication.instance.smartDownloadManager
        } catch (e: Exception) {
            Log.e(TAG, "LyroApplication instance unavailable: ${e.message}")
            return Result.failure()
        }

        return try {
            val result = manager.executeMaintenance(isManualRefresh = false)
            if (result.isSuccess) {
                Log.i(TAG, "SmartDownloadWorker completed successfully.")
                Result.success()
            } else {
                Log.w(TAG, "SmartDownloadWorker maintenance reported failure: ${result.exceptionOrNull()?.message}")
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "SmartDownloadWorker encountered unexpected error: ${e.message}", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
