package com.futo.platformplayer.views.comments

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.*
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StatePolycentric
import userpackage.Protocol

class AddCommentView : LinearLayout {
    private val _textComment: TextView;

    private var _contextUrl: String? = null
    private var _ref: Protocol.Reference? = null
    private var _lastClickTime = 0L

    val onCommentAdded = Event1<IPlatformComment>();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_add_comment, this, true);

        _textComment = findViewById(R.id.edit_comment);
        _textComment.setOnClickListener {
            val cu = _contextUrl ?: return@setOnClickListener
            val ref = _ref ?: return@setOnClickListener

            val now = System.currentTimeMillis()
            if (now - _lastClickTime > 3000) {
                StatePolycentric.instance.requireLogin(context, "Please login to post a comment") {
                    try {
                        UIDialogs.showCommentDialog(context, cu, ref) { onCommentAdded.emit(it) };
                    } catch (e: Throwable) {
                        Logger.w(TAG, "Failed to post comment", e);
                        UIDialogs.toast(context, "Failed to post comment: " + e.message);
                    }
                };

                _lastClickTime = now
            }
        }
    }

    fun setContext(contextUrl: String?, ref: Protocol.Reference?) {
        _contextUrl = contextUrl;
        _ref = ref;
    }

    companion object {
        const val TAG = "AddCommentView"
    }
}