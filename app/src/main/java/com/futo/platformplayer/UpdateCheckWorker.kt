package com.futo.platformplayer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
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

                UpdateNotificationManager.showUpdateAvailableNotification(applicationContext, latestVersion)

                if (StateApp.instance.isMainActive) {
                    withContext(Dispatchers.Main) {
                        StateApp.withContext { ctx ->
                            try {
                                UIDialogs.showUpdateAvailableDialog(ctx, latestVersion, false)
                            } catch (t: Throwable) {
                                Logger.w(TAG, "Failed to show in-app update dialog from worker", t)
                            }
                        }
                    }
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
