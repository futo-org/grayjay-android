package com.futo.platformplayer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import com.futo.platformplayer.logging.Logger


class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            (intent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT))
        }

        Logger.i(TAG, "Received media button intent, keyCode: " + keyEvent?.keyCode)
        if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY -> MediaControlReceiver.onPlayReceived.emit()
                KeyEvent.KEYCODE_MEDIA_PAUSE -> MediaControlReceiver.onPauseReceived.emit()
                KeyEvent.KEYCODE_MEDIA_NEXT -> MediaControlReceiver.onNextReceived.emit()
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> MediaControlReceiver.onPreviousReceived.emit()
                KeyEvent.KEYCODE_MEDIA_STOP -> MediaControlReceiver.onCloseReceived.emit()
            }
        }
    }

    companion object {
        private val TAG = "MediaButtonReceiver"
    }
}