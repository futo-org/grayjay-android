package com.futo.platformplayer.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.MediaSession2Service.MediaNotification
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.concurrent.futures.ResolvableFuture
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.cache.ChannelContentCache
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
                    .setSilent(true)
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
            .setSilent(true)
            .setChannelId(notificationChannel.id)
            .setProgress(1, 0, true);

        manager.notify(12, notif.build());

        var lastNotifUpdate = OffsetDateTime.now();

        val newSubChanges = hashSetOf<Subscription>();
        val newItems = mutableListOf<IPlatformContent>();

        val now = OffsetDateTime.now();
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
                        if(sub.doNotifications && content.datetime?.let { it < now } == true)
                            contentNotifs.add(Pair(sub, content));
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
                    val thumbnail = if(contentNotif.second is IPlatformVideo) (contentNotif.second as IPlatformVideo).thumbnails.getHQThumbnail()
                        else null;
                    if(thumbnail != null)
                        Glide.with(appContext).asBitmap()
                            .load(thumbnail)
                            .into(object: CustomTarget<Bitmap>() {
                                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                    notifyNewContent(manager, notificationChannel, 13 + i, contentNotif.first, contentNotif.second, resource);
                                }
                                override fun onLoadCleared(placeholder: Drawable?) {}
                                override fun onLoadFailed(errorDrawable: Drawable?) {
                                    notifyNewContent(manager, notificationChannel, 13 + i, contentNotif.first, contentNotif.second, null);
                                }
                            })
                    else
                        notifyNewContent(manager, notificationChannel, 13 + i, contentNotif.first, contentNotif.second, null);
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

    fun notifyNewContent(manager: NotificationManager, notificationChannel: NotificationChannel, id: Int, sub: Subscription, content: IPlatformContent, thumbnail: Bitmap? = null) {
        val notifBuilder = NotificationCompat.Builder(appContext, notificationChannel.id)
            .setSmallIcon(com.futo.platformplayer.R.drawable.foreground)
            .setContentTitle("New by [${sub.channel.name}]")
            .setContentText("${content.name}")
            .setSilent(true)
            .setContentIntent(PendingIntent.getActivity(this.appContext, 0, MainActivity.getVideoIntent(this.appContext, content.url),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setChannelId(notificationChannel.id);
        if(thumbnail != null) {
            //notifBuilder.setLargeIcon(thumbnail);
            notifBuilder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(thumbnail).bigLargeIcon(null as Bitmap?));
        }
        manager.notify(id, notifBuilder.build());
    }
}