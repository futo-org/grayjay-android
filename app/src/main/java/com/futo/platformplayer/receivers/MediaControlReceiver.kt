package com.futo.platformplayer.receivers

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1


class MediaControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val act = intent?.getStringExtra(EXTRA_MEDIA_ACTION);
        Logger.i(TAG, "Received MediaControl Event $act");

        try {
            when (act) {
                EVENT_PLAY -> onPlayReceived.emit();
                EVENT_PAUSE -> onPauseReceived.emit();
                EVENT_NEXT -> onNextReceived.emit();
                EVENT_PREV -> onPreviousReceived.emit();
                EVENT_CLOSE -> onCloseReceived.emit();
                EVENT_BACKGROUND -> onBackgroundReceived.emit()
            }
        }
        catch(ex: Throwable) {
            Logger.w(TAG, "Failed to handle intent: ${act}");
        }
    }

    companion object {
        private val TAG = "MediaControlReceiver"

        const val EXTRA_MEDIA_ACTION = "MediaAction";

        const val EVENT_PLAY = "Play";
        const val EVENT_PAUSE = "Pause";
        const val EVENT_NEXT = "Next";
        const val EVENT_PREV = "Prev";
        const val EVENT_CLOSE = "Close";
        const val EVENT_BACKGROUND = "Background"

        val onPlayReceived = Event0();
        val onPauseReceived = Event0();
        val onNextReceived = Event0();
        val onPreviousReceived = Event0();
        val onBackgroundReceived = Event0();
        val onSeekToReceived = Event1<Long>();

        val onLowerVolumeReceived = Event0();

        val onCloseReceived = Event0()

        fun getPlayIntent(context: Context, code: Int = 0) : PendingIntent = PendingIntent.getBroadcast(context, code, Intent(context, MediaControlReceiver::class.java).apply {
            this.putExtra(EXTRA_MEDIA_ACTION, EVENT_PLAY);
        },PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT);
        fun getPauseIntent(context: Context, code: Int = 0) : PendingIntent = PendingIntent.getBroadcast(context, code, Intent(context, MediaControlReceiver::class.java).apply {
            this.putExtra(EXTRA_MEDIA_ACTION, EVENT_PAUSE);
        },PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT);
        fun getNextIntent(context: Context, code: Int = 0) : PendingIntent = PendingIntent.getBroadcast(context, code, Intent(context, MediaControlReceiver::class.java).apply {
            this.putExtra(EXTRA_MEDIA_ACTION, EVENT_NEXT);
        },PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT);
        fun getPrevIntent(context: Context, code: Int = 0) : PendingIntent = PendingIntent.getBroadcast(context, code, Intent(context, MediaControlReceiver::class.java).apply {
            this.putExtra(EXTRA_MEDIA_ACTION, EVENT_PREV);
        },PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT);
        fun getCloseIntent(context: Context, code: Int = 0) : PendingIntent = PendingIntent.getBroadcast(context, code, Intent(context, MediaControlReceiver::class.java).apply {
            this.putExtra(EXTRA_MEDIA_ACTION, EVENT_CLOSE);
        },PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT);
        fun getBackgroundIntent(context: Context, code: Int = 0) : PendingIntent = PendingIntent.getBroadcast(context, code, Intent(context, MediaControlReceiver::class.java).apply {
            this.putExtra(EXTRA_MEDIA_ACTION, EVENT_BACKGROUND);
        },PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT);
    }
}