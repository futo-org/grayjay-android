package com.futo.platformplayer

import android.app.Dialog
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateUpdate
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class UpdateDownloadService : Service() {

    companion object {
        private const val TAG = "UpdateDownloadService"
        const val EXTRA_VERSION = "version"
        const val EXTRA_CANCEL = "cancel"
        private const val MAX_RETRIES = 5
        private const val INITIAL_BACKOFF_MS = 5_000L
        private const val BUFFER_SIZE = 8 * 1024

        var updateDownloadedDialog: Dialog? = null
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    @Volatile
    private var isDownloading: Boolean = false

    @Volatile
    private var cancelRequested: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.getBooleanExtra(EXTRA_CANCEL, false)) {
            cancelRequested = true
            Logger.i(TAG, "Download cancel requested")
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val version = intent.getIntExtra(EXTRA_VERSION, 0)
        if (version == 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (isDownloading) {
            Logger.i(TAG, "Download already in progress, ignoring new start")
            return START_STICKY
        }

        isDownloading = true
        cancelRequested = false

        val notification = UpdateNotificationManager.buildDownloadProgressNotification(this, version, 0, true)
        startForeground(UpdateNotificationManager.NOTIF_ID_DOWNLOADING, notification)

        scope.launch {
            downloadApk(version)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private suspend fun downloadApk(version: Int) {
        val apkFile = StateUpdate.getApkFile(this, version)
        val partialFile = StateUpdate.getPartialApkFile(this, version)

        try {
            if (apkFile.exists() && apkFile.length() > 0L) {
                Logger.i(TAG, "APK already downloaded at ${apkFile.absolutePath}")
                onDownloadComplete(version, apkFile)
                return
            }

            var backoffMs = INITIAL_BACKOFF_MS

            for (attempt in 0 until MAX_RETRIES) {
                if (cancelRequested) {
                    Logger.i(TAG, "Download cancelled before attempt ${attempt + 1}")
                    break
                }

                try {
                    performDownload(StateUpdate.APK_URL, partialFile, version)

                    if (!cancelRequested) {
                        if (apkFile.exists()) {
                            apkFile.delete()
                        }
                        if (!partialFile.renameTo(apkFile)) {
                            throw IllegalStateException("Failed to rename partial APK file")
                        }
                        onDownloadComplete(version, apkFile)
                    }
                    break
                } catch (t: Throwable) {
                    if (cancelRequested) {
                        Logger.i(TAG, "Download cancelled by user", t)
                        break
                    }

                    if (attempt == MAX_RETRIES - 1) {
                        Logger.e(TAG, "Download failed after ${attempt + 1} attempts", t)
                        UpdateNotificationManager.showDownloadFailedNotification(this, version, t)
                        break
                    } else {
                        Logger.w(TAG, "Download attempt ${attempt + 1} failed, retrying in ${backoffMs / 1000}s", t)
                        delay(backoffMs)
                        backoffMs *= 2
                    }
                }
            }
        } finally {
            isDownloading = false
            cancelRequested = false
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun performDownload(url: String, partialFile: File, version: Int) {
        var startOffset = if (partialFile.exists()) partialFile.length() else 0L
        Logger.i(TAG, "Starting download. url=$url, existingBytes=$startOffset")

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                if (startOffset > 0L) {
                    setRequestProperty("Range", "bytes=$startOffset-")
                }
            }

            connection.connect()
            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK && startOffset > 0L) {
                Logger.w(TAG, "Server ignored Range header, restarting download from scratch")
                partialFile.delete()
                startOffset = 0L
            } else if (responseCode != HttpURLConnection.HTTP_OK &&
                responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw IllegalStateException("Unexpected HTTP response code $responseCode")
            }

            val contentLength = connection.contentLengthLong
            val totalBytes = if (contentLength > 0L) startOffset + contentLength else -1L

            val buffer = ByteArray(BUFFER_SIZE)
            var downloaded = 0L
            var lastProgress = -1

            connection.inputStream.use { input ->
                FileOutputStream(partialFile, startOffset > 0L).use { output ->
                    while (!cancelRequested) {
                        val read = input.read(buffer)
                        if (read == -1) {
                            break
                        }
                        output.write(buffer, 0, read)
                        downloaded += read

                        if (totalBytes > 0L) {
                            val progress = (((startOffset + downloaded) * 100L) / totalBytes).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                val safeProgress = when {
                                    progress < 0 -> 0
                                    progress > 100 -> 100
                                    else -> progress
                                }
                                UpdateNotificationManager.updateDownloadProgress(this, version, safeProgress, false)
                            }
                        } else {
                            UpdateNotificationManager.updateDownloadProgress(this, version, 0, true)
                        }
                    }
                    output.flush()
                }
            }

            if (cancelRequested) {
                throw CancellationException("Download cancelled")
            }

            if (totalBytes > 0L && startOffset + downloaded < totalBytes) {
                throw IllegalStateException("Download incomplete: expected=$totalBytes, got=${startOffset + downloaded}")
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun onDownloadComplete(version: Int, apkFile: File) {
        Logger.i(TAG, "Download complete for version=$version, file=${apkFile.absolutePath}")
        UpdateNotificationManager.showDownloadCompleteNotification(this, version, apkFile)

        if (StateApp.instance.isMainActive) {
            StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                StateApp.withContext { ctx ->
                    try {
                        updateDownloadedDialog = UIDialogs.showConfirmationDialog(ctx, "Update downloaded, press confirm to install", {
                            UpdateNotificationManager.cancelAll(ctx)
                            UpdateInstaller.startInstall(ctx, apkFile)
                        }, dismissAction = { updateDownloadedDialog = null })
                    } catch (t: Throwable) {
                        Logger.w(TAG, "Failed to show in-app update downloaded dialog", t)
                        updateDownloadedDialog = null
                    }
                }
            }
        }
    }
}
