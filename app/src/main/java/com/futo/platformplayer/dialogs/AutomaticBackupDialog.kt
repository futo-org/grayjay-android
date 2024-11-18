package com.futo.platformplayer.dialogs

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
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
    private lateinit var _buttonStart: LinearLayout;
    private lateinit var _buttonStop: LinearLayout;
    private lateinit var _buttonCancel: ImageButton;

    private lateinit var _editPassword: EditText;
    private lateinit var _editPassword2: EditText;

    private lateinit var _inputMethodManager: InputMethodManager;


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_automatic_backup, null));

        _buttonCancel = findViewById(R.id.button_cancel);
        _buttonStop = findViewById(R.id.button_stop);
        _buttonStart = findViewById(R.id.button_start);
        _editPassword = findViewById(R.id.edit_password);
        _editPassword2 = findViewById(R.id.edit_password2);

        _inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager;

        _buttonCancel.setOnClickListener {
            clearFocus();
            dismiss();
        };
        _buttonStop.setOnClickListener {
            clearFocus();
            dismiss();
            Settings.instance.backup.autoBackupPassword = null;
            Settings.instance.backup.didAskAutoBackup = true;
            Settings.instance.save();

            UIDialogs.toast(context, "AutoBackup disabled");
        }

        _buttonStart.setOnClickListener {
            val p1 = _editPassword.text.toString();
            val p2 = _editPassword2.text.toString();
            if(!(p1?.equals(p2) ?: false)) {
                UIDialogs.toast(context, "Password fields do not match, confirm that you typed it correctly.");
                return@setOnClickListener;
            }

            val pbytes = _editPassword.text.toString().toByteArray();
            if(pbytes.size < 4 || pbytes.size > 32) {
                UIDialogs.toast(context, "Password needs to be atleast 4 bytes long and smaller than 32 bytes", false);
                return@setOnClickListener;
            }
            clearFocus();
            dismiss();

            Logger.i(TAG, "Set AutoBackupPassword");
            Settings.instance.backup.autoBackupPassword = _editPassword.text.toString();
            Settings.instance.backup.didAskAutoBackup = true;
            Settings.instance.save();

            UIDialogs.toast(context, "AutoBackup enabled");
            try {
                StateBackup.startAutomaticBackup(true);
            }
            catch(ex: Throwable) {
                Logger.e(TAG, "Forced automatic backup failed", ex);
                UIDialogs.toast(context, "Automatic backup failed due to:\n" + ex.message);
            }
        };

        window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }

    private fun clearFocus() {
        _editPassword.clearFocus();
        currentFocus?.let { _inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0) };
    }

    companion object {
        private val TAG = "AutomaticBackupDialog";
    }
}