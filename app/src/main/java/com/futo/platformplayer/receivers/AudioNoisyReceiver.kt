package com.futo.platformplayer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.futo.platformplayer.logging.Logger


class AudioNoisyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        Logger.i(TAG, "Audio Noisy received");
        MediaControlReceiver.onPauseReceived.emit();
    }

    companion object {
        private val TAG = "AudioNoisyReceiver"
    }
}