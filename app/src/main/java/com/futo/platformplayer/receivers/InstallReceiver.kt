package com.futo.platformplayer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1


class InstallReceiver : BroadcastReceiver() {
    private val TAG = "InstallReceiver"

    companion object {
        val onReceiveResult = Event1<String?>();
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
        Logger.i(TAG, "Received status $status.");

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val activityIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (activityIntent == null) {
                    Logger.w(TAG, "Received STATUS_PENDING_USER_ACTION and activity intent is null.")
                    return;
                }
                context.startActivity(activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> onReceiveResult.emit(null);
            PackageInstaller.STATUS_FAILURE -> onReceiveResult.emit(context.getString(R.string.general_failure));
            PackageInstaller.STATUS_FAILURE_ABORTED -> onReceiveResult.emit(context.getString(R.string.aborted));
            PackageInstaller.STATUS_FAILURE_BLOCKED -> onReceiveResult.emit(context.getString(R.string.blocked));
            PackageInstaller.STATUS_FAILURE_CONFLICT -> onReceiveResult.emit(context.getString(R.string.conflict));
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> onReceiveResult.emit(context.getString(R.string.incompatible));
            PackageInstaller.STATUS_FAILURE_INVALID -> onReceiveResult.emit(context.getString(R.string.invalid));
            PackageInstaller.STATUS_FAILURE_STORAGE -> onReceiveResult.emit(context.getString(R.string.not_enough_storage));
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                onReceiveResult.emit(msg)
            }
        }
    }
}