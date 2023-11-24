package com.futo.platformplayer.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.futo.platformplayer.Settings
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.getNowDiffSeconds
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateNotifications
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.toHumanNowDiffStringMinDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

class BackgroundWorker(private val appContext: Context, private val workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        if(StateApp.instance.isMainActive && !inputData.getBoolean("bypassMainCheck", false)) {
            Logger.i("BackgroundWorker", "CANCELLED");
            return Result.success();
        }
        var exception: Throwable? = null;

        StateApp.instance.startBackground(appContext, true, true) {
            Logger.i("BackgroundWorker", "STARTED");
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
            val notificationChannel = NotificationChannel("backgroundWork", "Background Work",
                NotificationManager.IMPORTANCE_HIGH).apply {
                this.enableVibration(false);
                this.setSound(null, null);
            };
            notificationManager.createNotificationChannel(notificationChannel);
            val contentChannel = StateNotifications.instance.contentNotifChannel
            notificationManager.createNotificationChannel(contentChannel);
            try {
                    doSubscriptionUpdating(notificationManager, notificationChannel, contentChannel);
            }
            catch(ex: Throwable) {
                exception = ex;
                Logger.e("BackgroundWorker", "FAILED: ${ex.message}", ex);
                notificationManager.notify(14, NotificationCompat.Builder(appContext, notificationChannel.id)
                    .setSmallIcon(com.futo.platformplayer.R.drawable.foreground)
                    .setContentTitle("Grayjay")
                    .setContentText("Failed subscriptions update\n${ex.message}")
                    .setSilent(true)
                    .setChannelId(notificationChannel.id).build());
            }

        }

        return if(exception == null)
            Result.success()
        else
            Result.failure();
    }


    suspend fun doSubscriptionUpdating(manager: NotificationManager, backgroundChannel: NotificationChannel, contentChannel: NotificationChannel) {
        val notif = NotificationCompat.Builder(appContext, backgroundChannel.id)
            .setSmallIcon(com.futo.platformplayer.R.drawable.foreground)
            .setContentTitle("Grayjay")
            .setContentText("Updating subscriptions...")
            .setSilent(true)
            .setChannelId(backgroundChannel.id)
            .setProgress(1, 0, true);

        manager.notify(12, notif.build());

        var lastNotifUpdate = OffsetDateTime.now();

        val newSubChanges = hashSetOf<Subscription>();
        val newItems = mutableListOf<IPlatformContent>();

        val now = OffsetDateTime.now();
        val threeDays = now.minusDays(4);
        val contentNotifs = mutableListOf<Pair<Subscription, IPlatformContent>>();
        withContext(Dispatchers.IO) {
            val results = StateSubscriptions.instance.getSubscriptionsFeedWithExceptions(true, false,this, { progress, total ->
                Logger.i("BackgroundWorker", "SUBSCRIPTION PROGRESS: ${progress}/${total}");

                synchronized(manager) {
                    if (lastNotifUpdate.getNowDiffSeconds() > 1) {
                        notif.setContentText("Subscriptions (${progress}/${total})");
                        notif.setProgress(total, progress, false);
                        manager.notify(12, notif.build());
                        lastNotifUpdate = OffsetDateTime.now();
                    }
                }
            }, { sub, content ->
                synchronized(newSubChanges) {
                    if(!newSubChanges.contains(sub)) {
                        newSubChanges.add(sub);
                        if(sub.doNotifications) {
                            if(content.datetime != null) {
                                if(content.datetime!! <= now.plusMinutes(StateNotifications.instance.plannedWarningMinutesEarly) && content.datetime!! > threeDays)
                                    contentNotifs.add(Pair(sub, content));
                                else if(content.datetime!! > now.plusMinutes(StateNotifications.instance.plannedWarningMinutesEarly) && Settings.instance.notifications.plannedContentNotification)
                                    StateNotifications.instance.scheduleContentNotification(applicationContext, content);
                            }
                        }
                    }
                    newItems.add(content);
                }
            });

            //Only for testing notifications
            val testNotifs = 0;
            if(contentNotifs.size == 0 && testNotifs > 0) {
                results.first.getResults().filter { it is IPlatformVideo && it.datetime?.let { it < now } == true }
                    .take(testNotifs).forEach {
                        contentNotifs.add(Pair(StateSubscriptions.instance.getSubscriptions().first(), it));
                    }
            }
        }

        manager.cancel(12);

        if(contentNotifs.size > 0) {
            try {
                val items = contentNotifs.take(5).toList()
                for(i in items.indices) {
                    val contentNotif = items.get(i);
                    StateNotifications.instance.notifyNewContentWithThumbnail(appContext, manager, contentChannel, 13 + i, contentNotif.second);
                }
            }
            catch(ex: Throwable) {
                Logger.e("BackgroundWorker", "Failed to create notif", ex);
            }
        }
        /*
            manager.notify(13, NotificationCompat.Builder(appContext, notificationChannel.id)
                .setSmallIcon(com.futo.platformplayer.R.drawable.foreground)
                .setContentTitle("Grayjay")
                .setContentText("${newItems.size} new content from ${newSubChanges.size} creators")
                .setSilent(true)
                .setChannelId(notificationChannel.id).build());*/
    }
}