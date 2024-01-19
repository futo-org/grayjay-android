package com.futo.platformplayer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class AudioNoisyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
            Logger.i(TAG, "Audio Noisy received");
            MediaControlReceiver.onPauseReceived.emit();
        }
    }

    companion object {
        private val TAG = "AudioNoisyReceiver"
    }
}