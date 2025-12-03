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
        }
    }

    private fun handleUpdateYes(context: Context, intent: Intent) {
        AutoUpdateDialog.currentDialog?.dismiss()

        val version = intent.getIntExtra(UpdateNotificationManager.EXTRA_VERSION, 0)
        if (version == 0) {
            return
        }

        NotificationManagerCompat.from(context).cancel(UpdateNotificationManager.NOTIF_ID_AVAILABLE)

        val serviceIntent = Intent(context, UpdateDownloadService::class.java).apply {
            putExtra(UpdateDownloadService.EXTRA_VERSION, version)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
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
}
