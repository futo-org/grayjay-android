package com.futo.platformplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.dialogs.AutoUpdateDialog
import com.futo.platformplayer.states.StateApp
import java.io.File

class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UpdateNotificationManager.ACTION_UPDATE_YES -> handleUpdateYes(context, intent)
            UpdateNotificationManager.ACTION_UPDATE_NO -> handleUpdateNo(context)
            UpdateNotificationManager.ACTION_UPDATE_NEVER -> handleUpdateNever(context)
            UpdateNotificationManager.ACTION_DOWNLOAD_CANCEL -> handleDownloadCancel(context, intent)
            UpdateNotificationManager.ACTION_INSTALL_NOW -> handleInstallNow(context, intent)
        }
    }

    private fun handleUpdateYes(context: Context, intent: Intent) {
        AutoUpdateDialog.currentDialog?.dismiss()

        val version = intent.getIntExtra(UpdateNotificationManager.EXTRA_VERSION, 0)
        if (version == 0) {
            return
        }

        NotificationManagerCompat.from(context).cancel(UpdateNotificationManager.NOTIF_ID_AVAILABLE)

        if (Settings.instance.autoUpdate.backgroundDownload == 1) {
            val serviceIntent = Intent(context, UpdateDownloadService::class.java).apply {
                putExtra(UpdateDownloadService.EXTRA_VERSION, version)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            if (StateApp.instance.isMainActive) {
                StateApp.withContext { ctx ->
                    UIDialogs.showUpdateAvailableDialog(ctx, version, false)
                }
            } else {
                val startIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("SHOW_UPDATE_DIALOG_VERSION", version)
                }
                context.startActivity(startIntent)
            }
        }
    }

    private fun handleUpdateNo(context: Context) {
        AutoUpdateDialog.currentDialog?.dismiss()
        NotificationManagerCompat.from(context).cancel(UpdateNotificationManager.NOTIF_ID_AVAILABLE)
    }

    private fun handleUpdateNever(context: Context) {
        AutoUpdateDialog.currentDialog?.dismiss()
        Settings.instance.autoUpdate.check = 1
        Settings.instance.save()

        UpdateNotificationManager.cancelAll(context)
    }

    private fun handleDownloadCancel(context: Context, intent: Intent) {
        val version = intent.getIntExtra(UpdateNotificationManager.EXTRA_VERSION, 0)

        val cancelIntent = Intent(context, UpdateDownloadService::class.java).apply {
            putExtra(UpdateDownloadService.EXTRA_CANCEL, true)
            putExtra(UpdateDownloadService.EXTRA_VERSION, version)
        }
        ContextCompat.startForegroundService(context, cancelIntent)

        NotificationManagerCompat.from(context).cancel(UpdateNotificationManager.NOTIF_ID_DOWNLOADING)
    }

    private fun handleInstallNow(context: Context, intent: Intent) {
        val version = intent.getIntExtra(UpdateNotificationManager.EXTRA_VERSION, 0)
        val apkPath = intent.getStringExtra(UpdateNotificationManager.EXTRA_APK_PATH)

        if (version == 0 || apkPath.isNullOrEmpty()) {
            return
        }

        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            return
        }

        UpdateNotificationManager.cancelAll(context)
        UpdateInstaller.startInstall(context, apkFile)
        UpdateDownloadService.updateDownloadedDialog?.dismiss()
    }
}
