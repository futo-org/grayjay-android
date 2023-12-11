package com.futo.platformplayer.states

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.SerializedPlatformContent
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.receivers.PlannedNotificationReceiver
import com.futo.platformplayer.serializers.PlatformContentSerializer
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.toHumanNowDiffStringMinDay
import java.time.OffsetDateTime

class StateNotifications {
    private val _alarmManagerLock = Object();
    private var _alarmManager: AlarmManager? = null;
    val plannedWarningMinutesEarly: Long = 10;

    val contentNotifChannel = NotificationChannel("contentChannel", "Content Notifications",
        NotificationManager.IMPORTANCE_HIGH).apply {
        this.enableVibration(false);
        this.setSound(null, null);
    };

    private val _plannedContent = FragmentedStorage.storeJson<SerializedPlatformContent>("planned_content_notifs", PlatformContentSerializer())
        .load();

    private fun getAlarmManager(context: Context): AlarmManager {
        synchronized(_alarmManagerLock) {
            if(_alarmManager == null)
                _alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager;
            return _alarmManager!!;
        }
    }

    fun scheduleContentNotification(context: Context, content: IPlatformContent) {
            try {
            var existing = _plannedContent.findItem { it.url == content.url };
            if(existing != null) {
                _plannedContent.delete(existing);
                existing = null;
            }
            if(content.datetime != null) {
                val item = SerializedPlatformContent.fromContent(content);
                _plannedContent.saveAsync(item);

                val manager = getAlarmManager(context);
                val notifyDateTime = content.datetime!!.minusMinutes(plannedWarningMinutesEarly);
                if(Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
                    Logger.i(TAG, "Scheduling in-exact notification for [${content.name}] at ${notifyDateTime.toHumanNowDiffString()}")
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notifyDateTime.toEpochSecond().times(1000), PlannedNotificationReceiver.getIntent(context));
                }
                else {
                    Logger.i(TAG, "Scheduling exact notification for [${content.name}] at ${notifyDateTime.toHumanNowDiffString()}")
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notifyDateTime.toEpochSecond().times(1000), PlannedNotificationReceiver.getIntent(context))
                }
            }
        }
        catch(ex: Throwable) {
            Logger.e(TAG, "scheduleContentNotification failed for [${content.name}]", ex);
        }
    }
    fun removeChannelPlannedContent(channelUrl: String) {
        val toDeletes = _plannedContent.findItems { it.author.url == channelUrl };
        for(toDelete in toDeletes)
            _plannedContent.delete(toDelete);
    }

    fun getScheduledNotifications(secondsFuture: Long, deleteReturned: Boolean = false): List<SerializedPlatformContent> {
        val minDate = OffsetDateTime.now().plusSeconds(secondsFuture);
        val toNotify = _plannedContent.findItems { it.datetime?.let { it.isBefore(minDate) } == true }

        if(deleteReturned) {
            for(toDelete in toNotify)
                _plannedContent.delete(toDelete);
        }
        return toNotify;
    }

    fun notifyNewContentWithThumbnail(context: Context, manager: NotificationManager, notificationChannel: NotificationChannel, id: Int, content: IPlatformContent) {
        val thumbnail = if(content is IPlatformVideo) content.thumbnails.getHQThumbnail()
        else null;
        if(thumbnail != null)
            Glide.with(context).asBitmap()
                .load(thumbnail)
                .into(object: CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        notifyNewContent(context, manager, notificationChannel, id, content, resource);
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {}
                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        notifyNewContent(context, manager, notificationChannel, id, content, null);
                    }
                })
        else
            notifyNewContent(context, manager, notificationChannel, id, content, null);
    }

    fun notifyNewContent(context: Context, manager: NotificationManager, notificationChannel: NotificationChannel, id: Int, content: IPlatformContent, thumbnail: Bitmap? = null) {
        val notifBuilder = NotificationCompat.Builder(context, notificationChannel.id)
            .setSmallIcon(com.futo.platformplayer.R.drawable.foreground)
            .setContentTitle("New by [${content.author.name}]")
            .setContentText("${content.name}")
            .setSubText(content.datetime?.toHumanNowDiffStringMinDay())
            .setSilent(true)
            .setContentIntent(PendingIntent.getActivity(context, content.hashCode(), MainActivity.getVideoIntent(context, content.url),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setChannelId(notificationChannel.id);
        if(thumbnail != null) {
            //notifBuilder.setLargeIcon(thumbnail);
            notifBuilder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(thumbnail).bigLargeIcon(null as Bitmap?));
        }
        manager.notify(id, notifBuilder.build());
    }


    companion object {
        val TAG = "StateNotifications";
        private var _instance : StateNotifications? = null;
        val instance : StateNotifications
            get(){
                if(_instance == null)
                    _instance = StateNotifications();
                return _instance!!;
            };

        fun finish() {
            _instance?.let {
                _instance = null;
            }
        }
    }
}