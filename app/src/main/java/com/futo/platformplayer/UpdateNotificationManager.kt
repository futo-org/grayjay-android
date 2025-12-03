package com.futo.platformplayer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.PendingIntent.getBroadcast
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.io.File

object UpdateNotificationManager {
    private const val CHANNEL_ID = "app_updates"
    private const val CHANNEL_NAME = "App updates"
    private const val CHANNEL_DESCRIPTION = "Notifications about new app versions"

    const val ACTION_UPDATE_YES = "com.futo.platformplayer.UPDATE_YES"
    const val ACTION_UPDATE_NO = "com.futo.platformplayer.UPDATE_NO"
    const val ACTION_UPDATE_NEVER = "com.futo.platformplayer.UPDATE_NEVER"
    const val ACTION_DOWNLOAD_CANCEL = "com.futo.platformplayer.UPDATE_CANCEL"
    const val ACTION_INSTALL_NOW = "com.futo.platformplayer.UPDATE_INSTALL"

    const val EXTRA_VERSION = "version"
    const val EXTRA_APK_PATH = "apk_path"

    const val NOTIF_ID_AVAILABLE = 2001
    const val NOTIF_ID_DOWNLOADING = 2002
    const val NOTIF_ID_READY = 2003

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
    }


    fun showUpdateAvailableNotification(context: Context, version: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        ensureChannel(context)

        val yesIntent = Intent(context, UpdateActionReceiver::class.java).apply {
            action = ACTION_UPDATE_YES
            putExtra(EXTRA_VERSION, version)
        }
        val yesPendingIntent = getBroadcast(context, 0, yesIntent, FLAG_MUTABLE or FLAG_UPDATE_CURRENT)
        val noIntent = Intent(context, UpdateActionReceiver::class.java).apply {
            action = ACTION_UPDATE_NO
            putExtra(EXTRA_VERSION, version)
        }
        val noPendingIntent = getBroadcast(context, 1, noIntent, FLAG_MUTABLE or FLAG_UPDATE_CURRENT)
        val neverIntent = Intent(context, UpdateActionReceiver::class.java).apply {
            action = ACTION_UPDATE_NEVER
            putExtra(EXTRA_VERSION, version)
        }
        val neverPendingIntent = getBroadcast(context, 2, neverIntent, FLAG_MUTABLE or FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.foreground)
            .setContentTitle("Update available")
            .setContentText("A new version ($version) is available.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setSilent(true)
            .addAction(0, "Download", yesPendingIntent)
            .addAction(0, "Not now", noPendingIntent)
            .addAction(0, "Never", neverPendingIntent)

        NotificationManagerCompat.from(context).notify(NOTIF_ID_AVAILABLE, builder.build())
    }

    fun buildDownloadProgressNotification(context: Context, version: Int, progress: Int, indeterminate: Boolean): Notification {
        ensureChannel(context)

        val cancelIntent = Intent(context, UpdateActionReceiver::class.java).apply {
            action = ACTION_DOWNLOAD_CANCEL
            putExtra(EXTRA_VERSION, version)
        }
        val cancelPendingIntent = getBroadcast(
            context,
            3,
            cancelIntent,
            FLAG_MUTABLE or FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.foreground)
            .setContentTitle("Downloading update")
            .setContentText("Downloading version $version")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Cancel", cancelPendingIntent)

        if (indeterminate) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, progress, false)
        }

        return builder.build()
    }

    fun updateDownloadProgress(context: Context, version: Int, progress: Int, indeterminate: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val notification = buildDownloadProgressNotification(context, version, progress, indeterminate)
        NotificationManagerCompat.from(context).notify(NOTIF_ID_DOWNLOADING, notification)
    }


    fun showDownloadCompleteNotification(context: Context, version: Int, apkFile: File) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        ensureChannel(context)

        val installIntent = Intent(context, UpdateActionReceiver::class.java).apply {
            action = ACTION_INSTALL_NOW
            putExtra(EXTRA_VERSION, version)
            putExtra(EXTRA_APK_PATH, apkFile.absolutePath)
        }
        val installPendingIntent = getBroadcast(
            context,
            4,
            installIntent,
            FLAG_MUTABLE or FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.foreground)
            .setContentTitle("Update downloaded")
            .setContentText("Tap to install version $version.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setSilent(true)
            .addAction(0, "Install", installPendingIntent)

        NotificationManagerCompat.from(context).notify(NOTIF_ID_READY, builder.build())
    }


    fun showDownloadFailedNotification(context: Context, version: Int, error: Throwable?) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        ensureChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.foreground)
            .setContentTitle("Failed to download update")
            .setContentText(error?.message ?: "Unknown error")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setSilent(true)

        NotificationManagerCompat.from(context).notify(NOTIF_ID_READY, builder.build())
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_AVAILABLE)
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_DOWNLOADING)
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_READY)
    }
}
