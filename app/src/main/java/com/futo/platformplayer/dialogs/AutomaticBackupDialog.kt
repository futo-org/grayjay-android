package com.futo.platformplayer.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateBackup
import com.google.android.material.button.MaterialButton


class AutomaticBackupDialog(context: Context) : AlertDialog(context) {
    private lateinit var _buttonStart: LinearLayout
    private lateinit var _buttonStop: LinearLayout
    private lateinit var _buttonCancel: ImageButton
    private lateinit var _imm: InputMethodManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_automatic_backup, null))

        _buttonCancel = findViewById(R.id.button_cancel)
        _buttonStop = findViewById(R.id.button_stop)
        _buttonStart = findViewById(R.id.button_start)

        _imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        _buttonStart.visibility = if (Settings.instance.backup.autoBackupEnabled) View.GONE else View.VISIBLE
        _buttonStop.visibility  = if (Settings.instance.backup.autoBackupEnabled) View.VISIBLE else View.GONE

        _buttonCancel.setOnClickListener {
            dismiss()
        }

        _buttonStop.setOnClickListener {
            dismiss()
            Settings.instance.backup.autoBackupEnabled = false
            Settings.instance.backup.autoBackupPassword = null
            Settings.instance.backup.didAskAutoBackup = true
            Settings.instance.save()
            UIDialogs.toast(context, context.getString(R.string.automatic_backup_disabled))
        }

        _buttonStart.setOnClickListener {
            dismiss()
            Logger.i(TAG, "Enable AutoBackup (unencrypted)")

            val activity = StateApp.instance.activity as? Activity
            if (activity == null) {
                UIDialogs.toast(context, "No activity available")
                return@setOnClickListener
            }

            dismiss()

            Logger.i(TAG, "Enable AutoBackup")
            Settings.instance.backup.autoBackupPassword = null
            Settings.instance.backup.didAskAutoBackup = true
            Settings.instance.save()

            UIDialogs.toast(context, "AutoBackup enabled")
            try {
                StateBackup.startAutomaticBackup(true)
            } catch (ex: Throwable) {
                Logger.e(TAG, "Forced automatic backup failed", ex)
                UIDialogs.toast(context, "Automatic backup failed due to:\n" + ex.message)
            }

            Settings.instance.backup.autoBackupEnabled = true
            Settings.instance.backup.autoBackupPassword = null
            Settings.instance.backup.didAskAutoBackup = true
            Settings.instance.save()

            UIDialogs.toast(context, context.getString(R.string.automatic_backup_enabled))
            try {
                StateBackup.startAutomaticBackup(true)
            } catch (ex: Throwable) {
                Logger.e(TAG, "Forced automatic backup failed", ex)
                UIDialogs.toast(context, "Automatic backup failed due to:\n" + ex.message)
            }
        }

        window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }

    companion object {
        private const val TAG = "AutomaticBackupDialog"
    }
}