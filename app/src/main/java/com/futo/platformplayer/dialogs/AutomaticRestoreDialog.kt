package com.futo.platformplayer.dialogs

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateBackup
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutomaticRestoreDialog(context: Context, private val scope: CoroutineScope) : AlertDialog(context) {

    private lateinit var _buttonStart: LinearLayout
    private lateinit var _buttonCancel: MaterialButton
    private lateinit var _textReason: TextView
    private lateinit var _editPassword: EditText
    private lateinit var _passwordContainer: LinearLayout
    private lateinit var _icon: ImageView
    private lateinit var _progress: ProgressBar
    private lateinit var _textStart: TextView
    private lateinit var _imm: InputMethodManager

    private var _needsPassword: Boolean = true
    private var _detectJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_automatic_backup_restore, null))

        _buttonCancel = findViewById(R.id.button_cancel)
        _buttonStart = findViewById(R.id.button_start)
        _editPassword = findViewById(R.id.edit_password)
        _textReason = findViewById(R.id.text_reason)
        _passwordContainer = findViewById(R.id.password_container)
        _icon = findViewById(R.id.image_icon)
        _progress = findViewById(R.id.progress_restore)
        _textStart = findViewById(R.id.text_start)

        _imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        _needsPassword = true
        applyMode(needsPassword = true)
        setBusy(true, labelRes = R.string.checking_backup, lockCancel = false)

        _buttonCancel.setOnClickListener {
            clearFocus()
            dismiss()
        }
        _buttonStart.setOnClickListener { onStartClicked() }
        window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }

    override fun onStart() {
        super.onStart()

        _detectJob?.cancel()
        _detectJob = scope.launch(Dispatchers.Main) {
            val needs = try {
                StateBackup.requiresPasswordForAutomaticBackup(context)
            } catch (_: Throwable) {
                true
            }

            if (!isShowing) return@launch
            _needsPassword = needs
            applyMode(needsPassword = needs)
            setBusy(false)
        }
    }

    override fun onStop() {
        _detectJob?.cancel()
        _detectJob = null
        super.onStop()
    }

    private fun applyMode(needsPassword: Boolean) {
        _textStart.setText(R.string.restore)
        if (needsPassword) {
            _icon.setImageResource(R.drawable.ic_lock)
            _passwordContainer.visibility = View.VISIBLE
            _editPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            _textReason.setText(R.string.it_appears_an_automatic_backup_exists_on_your_device_if_you_would_like_to_restore_enter_your_backup_password)
        } else {
            _icon.setImageResource(R.drawable.ic_move_up)
            _passwordContainer.visibility = View.GONE
            _editPassword.setText("")
            _textReason.setText(R.string.automatic_backup_found_no_password)
        }
    }

    private fun onStartClicked() {
        val password = _editPassword.text?.toString() ?: ""

        if (_needsPassword) {
            val pbytes = password.toByteArray()
            if (pbytes.size < 4 || pbytes.size > 32) {
                _editPassword.error = context.getString(R.string.backup_password_length_error)
                _editPassword.requestFocus()
                return
            }
        }

        clearFocus()
        setBusy(true, labelRes = R.string.restoring, lockCancel = true)

        scope.launch(Dispatchers.IO) {
            try {
                StateBackup.restoreAutomaticBackup(context, scope, if (_needsPassword) password else "", true)
                withContext(Dispatchers.Main) {
                    if (isShowing) dismiss()
                }
            } catch (ex: Throwable) {
                Logger.e(TAG, "Failed to restore automatic backup", ex)
                withContext(Dispatchers.Main) {
                    if (!isShowing) return@withContext
                    setBusy(false)
                    UIDialogs.showGeneralErrorDialog(context, "Restore failed", ex)
                }
            }
        }
    }

    private fun setBusy(busy: Boolean, labelRes: Int = R.string.restore, lockCancel: Boolean = busy) {
        _progress.visibility = if (busy) View.VISIBLE else View.GONE
        _buttonCancel.isEnabled = !lockCancel
        _buttonStart.isEnabled = !busy
        _editPassword.isEnabled = !busy && _needsPassword
        _buttonStart.alpha = if (busy) 0.6f else 1.0f
        _textStart.setText(labelRes)
    }

    private fun clearFocus() {
        _editPassword.clearFocus()
        currentFocus?.let { _imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    companion object {
        private const val TAG = "AutomaticRestoreDialog"
    }
}
