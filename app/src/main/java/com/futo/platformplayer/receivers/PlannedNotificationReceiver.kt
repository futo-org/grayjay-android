package com.futo.platformplayer.receivers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.futo.platformplayer.Settings
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateNotifications


class PlannedNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            Logger.i(TAG, "Planned Notification received");
            if(!Settings.instance.notifications.plannedContentNotification)
                return;
            if(StateApp.instance.contextOrNull == null)
                StateApp.instance.initializeFiles();

            val notifs = StateNotifications.instance.getScheduledNotifications(60 * 15, true);
            if(!notifs.isEmpty() && context != null) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
                val channel = StateNotifications.instance.contentNotifChannel;
                notificationManager.createNotificationChannel(channel);
                var i = 0;
                for (notif in notifs) {
                    StateNotifications.instance.notifyNewContentWithThumbnail(context, notificationManager, channel, 110 + i, notif);
                    i++;
                }
            }
        }
        catch(ex: Throwable) {
            Logger.e(TAG, "Failed PlannedNotificationReceiver.onReceive", ex);
        }
    }

    companion object {
        private val TAG = "PlannedNotificationReceiver"

        fun getIntent(context: Context): PendingIntent {
            return PendingIntent.getBroadcast(context, 110, Intent(context, PlannedNotificationReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);
        }
    }
}