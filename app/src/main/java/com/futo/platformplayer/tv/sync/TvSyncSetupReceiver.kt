package com.futo.platformplayer.tv.sync

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.futo.platformplayer.states.StateSync
import com.futo.platformplayer.tv.TvMode

class TvSyncSetupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PAIRING || !isOrderedBroadcast || !TvMode.isEnabled(context))
            return
        val service = StateSync.instance.syncService ?: return
        if (!service.serverSocketStarted)
            return
        val key = service.publicKey ?: return
        val code = service.pairingCode ?: return
        setResult(1, TvSyncPairingCode.url(key, code), null)
    }

    fun register(context: Context) {
        // Only the authorized ADB shell (or a privileged system app) may read the pairing code.
        ContextCompat.registerReceiver(context, this, IntentFilter(ACTION_PAIRING),
            Manifest.permission.DUMP, null, ContextCompat.RECEIVER_EXPORTED)
    }

    companion object {
        const val ACTION_OPEN = "com.futo.platformplayer.tv.OPEN_SYNC_SETUP"
        const val ACTION_PAIRING = "com.futo.platformplayer.tv.READ_SYNC_PAIRING"
    }
}
