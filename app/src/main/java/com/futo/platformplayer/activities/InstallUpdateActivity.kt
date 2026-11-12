package com.futo.platformplayer.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.UpdateInstaller
import com.futo.platformplayer.UpdateNotificationManager
import com.futo.platformplayer.logging.Logger
import java.io.File

class InstallUpdateActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        UpdateNotificationManager.cancelAll(this)

        val version = intent.getIntExtra(UpdateNotificationManager.EXTRA_VERSION, 0)
        val apkPath = intent.getStringExtra(UpdateNotificationManager.EXTRA_APK_PATH)

        if (version == 0 || apkPath.isNullOrEmpty()) {
            Logger.w("InstallUpdateActivity", "Missing version or apkPath")
            finish()
            return
        }

        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            Logger.w("InstallUpdateActivity", "APK file does not exist: $apkPath")
            UIDialogs.Companion.toast(this, "Update file missing")
            finish()
            return
        }

        UpdateInstaller.startInstall(this, version, apkFile)
        finish()
    }

    companion object {
        fun createIntent(context: Context, version: Int, apkPath: String): Intent =
            Intent(context, InstallUpdateActivity::class.java).apply {
                putExtra(UpdateNotificationManager.EXTRA_VERSION, version)
                putExtra(UpdateNotificationManager.EXTRA_APK_PATH, apkPath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
