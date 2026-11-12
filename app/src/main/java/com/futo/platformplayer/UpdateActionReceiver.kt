package com.futo.platformplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UpdateNotificationManager.ACTION_DOWNLOAD_CANCEL -> handleDownloadCancel(context, intent)
        }
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
