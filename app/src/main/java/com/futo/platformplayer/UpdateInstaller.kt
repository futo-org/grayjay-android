package com.futo.platformplayer

import android.annotation.SuppressLint
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.PendingIntent.getBroadcast
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.graphics.drawable.Animatable
import android.provider.Settings
import android.view.View
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.receivers.InstallReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import androidx.core.net.toUri
import com.futo.platformplayer.dialogs.AutoUpdateDialog
import com.futo.platformplayer.states.StateApp

object UpdateInstaller {
    private const val TAG = "UpdateInstaller"

    @SuppressLint("RequestInstallPackagesPolicy")
    fun startInstall(context: Context, version: Int, apkFile: File) {
        if (!apkFile.exists()) {
            Logger.w(TAG, "APK file does not exist: ${apkFile.absolutePath}")
            UIDialogs.toast(context, "Update file missing")
            UpdateNotificationManager.showInstallFailedNotification(context, version, apkFile, "APK file does not exist.")
            return
        }

        if (BuildConfig.IS_PLAYSTORE_BUILD) {
            UIDialogs.toast(context, "Updates are managed by the Play Store")
            UpdateNotificationManager.showInstallFailedNotification(context, version, apkFile, "Updates are managed by the Play Store.")
            return
        }

        try {
            val pm = context.packageManager
            if (!pm.canRequestPackageInstalls()) {
                UIDialogs.toast(context, "Allow this app to install updates, then try again")
                UpdateNotificationManager.showInstallFailedNotification(context, version, apkFile, "Install update permission was missing.")

                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = "package:${context.packageName}".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        } catch (t: Throwable) {
            Logger.e(TAG, "Failed to check unknown sources permission", t)
        }

        GlobalScope.launch(Dispatchers.IO) {
            var inputStream: InputStream? = null
            var session: PackageInstaller.Session? = null
            try {

                val packageInstaller: PackageInstaller = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val sessionId = packageInstaller.createSession(params)
                session = packageInstaller.openSession(sessionId)

                inputStream = apkFile.inputStream()
                val dataLength = apkFile.length()

                session.openWrite("package", 0, dataLength).use { sessionStream ->
                    inputStream.copyToOutputStream(dataLength, sessionStream) { _ -> }
                    session.fsync(sessionStream)
                }

                val intent = Intent(context, InstallReceiver::class.java).apply {
                    putExtra(UpdateNotificationManager.EXTRA_VERSION, version)
                    putExtra(UpdateNotificationManager.EXTRA_APK_PATH, apkFile.absolutePath)
                }
                val pendingIntent = getBroadcast(context, 0, intent, FLAG_MUTABLE or FLAG_UPDATE_CURRENT)
                val statusReceiver = pendingIntent.intentSender

                InstallReceiver.onReceiveResult.subscribe(this) { message ->
                    InstallReceiver.onReceiveResult.clear();
                    onReceiveResult(context, version, apkFile, message);
                };
                Logger.i(TAG, "Committing install session for ${apkFile.absolutePath}")
                session.commit(statusReceiver)
            } catch (e: Throwable) {
                Logger.w(TAG, "Exception while installing update", e)
                session?.abandon()
                withContext(Dispatchers.Main) {
                    UIDialogs.toast(context, "Failed to install update: ${e.message}")
                }

                UpdateNotificationManager.showInstallFailedNotification(context, version, apkFile, e.message)
            } finally {
                session?.close()
                inputStream?.close()
            }
        }
    }

    private fun onReceiveResult(context: Context, version: Int, apkFile: File, result: String?) {
        try {
            InstallReceiver.onReceiveResult.remove(this)

            if (result.isNullOrEmpty()) {
                Logger.i(TAG, "Update install finished successfully")
                UpdateNotificationManager.showInstallSucceededNotification(context, version)
            } else {
                Logger.w(TAG, "Update install failed: $result")
                UpdateNotificationManager.showInstallFailedNotification(context, version, apkFile, result)
                UIDialogs.showGeneralErrorDialog(context, "Install failed due to:\n$result")
            }
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to handle install result", e)
        }
    }
}
