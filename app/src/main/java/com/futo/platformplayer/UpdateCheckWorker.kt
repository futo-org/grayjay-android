package com.futo.platformplayer

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateCheckWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!Settings.instance.autoUpdate.isAutoUpdateEnabled()) {
            Logger.i(TAG, "Auto-update disabled, skipping worker run")
            return Result.success()
        }

        return withContext(Dispatchers.IO) {
            try {
                val client = ManagedHttpClient()
                val latestVersion = StateUpdate.Companion.instance.downloadVersionCode(client)

                if (latestVersion == null) {
                    Logger.w(TAG, "Failed to fetch latest version in worker")
                    return@withContext Result.retry()
                }

                val currentVersion = BuildConfig.VERSION_CODE
                Logger.i(TAG, "Worker check: current=$currentVersion, latest=$latestVersion")

                if (latestVersion <= currentVersion) {
                    return@withContext Result.success()
                }

                StateUpdate.Companion.instance.setUiAvailable(latestVersion)

                try {
                    val serviceIntent = Intent(applicationContext, UpdateDownloadService::class.java).apply {
                        putExtra(UpdateDownloadService.EXTRA_VERSION, latestVersion)
                    }
                    ContextCompat.startForegroundService(applicationContext, serviceIntent)
                } catch (t: Throwable) {
                    Logger.w(TAG, "Failed to start UpdateDownloadService", t)
                    StateUpdate.Companion.instance.setUiFailed(latestVersion, t.message)
                }

                Result.success()
            } catch (t: Throwable) {
                Logger.w(TAG, "Exception in UpdateCheckWorker", t)
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "UpdateCheckWorker"
        const val UNIQUE_WORK_NAME = "updateCheck"
    }
}
