package com.futo.platformplayer.dialogs

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.dp
import com.futo.platformplayer.fullyBackfillServersAnnounceExceptions
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.selectBestImage
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import com.futo.polycentric.core.ClaimType
import com.futo.polycentric.core.Store
import com.futo.polycentric.core.SystemState
import com.futo.polycentric.core.systemToURLInfoSystemLinkUrl
import com.futo.polycentric.core.toURLInfoSystemLinkUrl
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import userpackage.Protocol
import java.time.OffsetDateTime


class CommentDialog(context: Context?, val contextUrl: String, val ref: Protocol.Reference) : AlertDialog(context) {
    private lateinit var _buttonCreate: LinearLayout;
    private lateinit var _buttonCancel: MaterialButton;
    private lateinit var _editComment: EditText;
    private lateinit var _inputMethodManager: InputMethodManager;
    private lateinit var _textCharacterCount: TextView;
    private lateinit var _textCharacterCountMax: TextView;

    val onCommentAdded = Event1<IPlatformComment>();

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_comment, null));

        _buttonCancel = findViewById(R.id.button_cancel);
        _buttonCreate = findViewById(R.id.button_create);
        _editComment = findViewById(R.id.edit_comment);
        _textCharacterCount = findViewById(R.id.character_count);
        _textCharacterCountMax = findViewById(R.id.character_count_max);
        setCanceledOnTouchOutside(false)
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                handleCloseAttempt()
                true
            } else {
                false
            }
        }

        _editComment.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = Unit
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, c: Int) {
                val count = s?.length ?: 0;
                _textCharacterCount.text = count.toString();

                if (count > PolycentricPlatformComment.MAX_COMMENT_SIZE) {
                    _textCharacterCount.setTextColor(Color.RED);
                    _textCharacterCountMax.setTextColor(Color.RED);
                    _buttonCreate.alpha = 0.4f;
                } else {
                    _textCharacterCount.setTextColor(Color.WHITE);
                    _textCharacterCountMax.setTextColor(Color.WHITE);
                    _buttonCreate.alpha = 1.0f;
                }
            }
        });

        _inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager;

        _buttonCancel.setOnClickListener {
            handleCloseAttempt()
        };

        setOnCancelListener {
            handleCloseAttempt()
        }

        _buttonCreate.setOnClickListener {
            clearFocus();

            if (_editComment.text.count() > PolycentricPlatformComment.MAX_COMMENT_SIZE) {
                UIDialogs.toast(context, "Comment should be less than 5000 characters");
                return@setOnClickListener;
            }

            if (_editComment.text.isBlank()) {
                UIDialogs.toast(context, "Comment should not be blank.");
                return@setOnClickListener;
            }

            val comment = _editComment.text.toString();
            val processHandle = StatePolycentric.instance.processHandle!!
            val eventPointer = processHandle.post(comment, ref)

            StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                try {
                    Logger.i(TAG, "Started backfill");
                    processHandle.fullyBackfillServersAnnounceExceptions()
                    Logger.i(TAG, "Finished backfill");
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to backfill servers.", e);
                }
            }
            val systemState = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(processHandle.system))
            val dp_25 = 25.dp(context.resources)
            onCommentAdded.emit(PolycentricPlatformComment(
                contextUrl = contextUrl,
                author = PlatformAuthorLink(
                    id = PlatformID("polycentric", processHandle.system.systemToURLInfoSystemLinkUrl(systemState.servers.toList()), null, ClaimType.POLYCENTRIC.value.toInt()),
                    name = systemState.username,
                    url = processHandle.system.systemToURLInfoSystemLinkUrl(systemState.servers.toList()),
                    thumbnail = systemState.avatar.selectBestImage(dp_25 * dp_25)?.toURLInfoSystemLinkUrl(processHandle, systemState.servers.toList()),
                    subscribers = null
                ),
                msg = comment,
                rating = RatingLikeDislikes(0, 0),
                date = OffsetDateTime.now(),
                eventPointer = eventPointer,
                parentReference = ref
            ));

            dismiss();
        };

        window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        focus();
    }

    private fun handleCloseAttempt() {
        if (_editComment.text.isEmpty()) {
            clearFocus()
            dismiss()
        } else {
            UIDialogs.showConfirmationDialog(
                context,
                context.resources.getString(R.string.not_empty_close),
                action = {
                    clearFocus()
                    dismiss()
                }
            )
        }
    }

    private fun focus() {
        _editComment.requestFocus();
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    private fun clearFocus() {
        _editComment.clearFocus();
        currentFocus?.let { _inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0) };
    }

    companion object {
        private val TAG = "CommentDialog";
    }
}