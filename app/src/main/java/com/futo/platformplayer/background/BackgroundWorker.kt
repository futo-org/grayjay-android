package com.futo.platformplayer.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.concurrent.futures.ResolvableFuture
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.getNowDiffSeconds
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.views.adapters.viewholders.TabViewHolder
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

class BackgroundWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        if(StateApp.instance.isMainActive) {
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
            try {
                    doSubscriptionUpdating(notificationManager, notificationChannel);
            }
            catch(ex: Throwable) {
                exception = ex;
                Logger.e("BackgroundWorker", "FAILED: ${ex.message}", ex);
                notificationManager.notify(14, NotificationCompat.Builder(appContext, notificationChannel.id)
                    .setSmallIcon(com.futo.platformplayer.R.drawable.foreground)
                    .setContentTitle("Grayjay")
                    .setContentText("Failed subscriptions update\n${ex.message}")
                    .setChannelId(notificationChannel.id).build());
            }

        }

        return if(exception == null)
            Result.success()
        else
            Result.failure();
    }


    suspend fun doSubscriptionUpdating(manager: NotificationManager, notificationChannel: NotificationChannel) {
        val notif = NotificationCompat.Builder(appContext, notificationChannel.id)
            .setSmallIcon(com.futo.platformplayer.R.drawable.foreground)
            .setContentTitle("Grayjay")
            .setContentText("Updating subscriptions...")
            .setChannelId(notificationChannel.id)
            .setProgress(1, 0, true);

        manager.notify(12, notif.build());

        var lastNotifUpdate = OffsetDateTime.now();

        val newSubChanges = hashSetOf<Subscription>();
        val newItems = mutableListOf<IPlatformContent>();
        withContext(Dispatchers.IO) {
            StateSubscriptions.instance.getSubscriptionsFeedWithExceptions(true, false,this, { progress, total ->
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
                    if(!newSubChanges.contains(sub))
                        newSubChanges.add(sub);
                    newItems.add(content);
                }
            });
        }

        manager.cancel(12);

        if(newItems.size > 0)
            manager.notify(13, NotificationCompat.Builder(appContext, notificationChannel.id)
                .setSmallIcon(com.futo.platformplayer.R.drawable.foreground)
                .setContentTitle("Grayjay")
                .setContentText("${newItems.size} new content from ${newSubChanges.size} creators")
                .setChannelId(notificationChannel.id).build());
    }
}