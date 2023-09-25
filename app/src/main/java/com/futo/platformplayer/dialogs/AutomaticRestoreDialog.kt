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
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.dp
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.selectBestImage
import com.futo.platformplayer.states.StateAnnouncement
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateBackup
import com.futo.platformplayer.states.StatePolycentric
import com.futo.polycentric.core.*
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import userpackage.Protocol
import java.time.OffsetDateTime


class AutomaticRestoreDialog(context: Context, val scope: CoroutineScope) : AlertDialog(context) {
    private lateinit var _buttonStart: LinearLayout;
    private lateinit var _buttonCancel: MaterialButton;

    private lateinit var _editPassword: EditText;

    private lateinit var _inputMethodManager: InputMethodManager;


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_automatic_backup_restore, null));

        _buttonCancel = findViewById(R.id.button_cancel);
        _buttonStart = findViewById(R.id.button_start);
        _editPassword = findViewById(R.id.edit_password);

        _inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager;

        _buttonCancel.setOnClickListener {
            clearFocus();
            dismiss();
        };

        _buttonStart.setOnClickListener {
            val pbytes = _editPassword.text.toString().toByteArray();
            if(pbytes.size < 4 || pbytes.size > 32) {
                UIDialogs.toast(context, "Password needs to be atleast 4 bytes long and less than 32 bytes", false);
                return@setOnClickListener;
            }
            clearFocus();

            try {
                StateBackup.restoreAutomaticBackup(context, scope, _editPassword.text.toString(), true);
                dismiss();
            }
            catch(ex: Throwable) {
                Logger.e(TAG, "Failed to restore automatic backup", ex);
                //UIDialogs.toast(context, "Restore failed due to:\n" + ex.message);
                UIDialogs.showGeneralErrorDialog(context, "Restore failed", ex);
            }
        };

        window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }

    private fun clearFocus() {
        _editPassword.clearFocus();
        currentFocus?.let { _inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0) };
    }

    companion object {
        private val TAG = "AutomaticRestoreDialog";
    }
}