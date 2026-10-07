package com.futo.platformplayer.views.comments

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.*
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StatePolycentric

class AddCommentView : LinearLayout {
    private val _textComment: TextView;

    private var _contextUrl: String? = null
    private var _parent: PolycentricPlatformComment? = null
    private var _lastClickTime = 0L

    val onCommentAdded = Event1<IPlatformComment>();

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_add_comment, this, true);

        _textComment = findViewById(R.id.edit_comment);
        _textComment.setOnClickListener {
            val cu = _contextUrl ?: return@setOnClickListener

            val now = System.currentTimeMillis()
            if (now - _lastClickTime > 3000) {
                StatePolycentric.instance.requireLogin(context, context.getString(R.string.please_login_to_post_a_comment)) {
                    try {
                        UIDialogs.showCommentDialog(context, cu, _parent) { onCommentAdded.emit(it) };
                    } catch (e: Throwable) {
                        Logger.w(TAG, "Failed to post comment", e);
                        UIDialogs.toast(context, context.getString(R.string.failed_to_post_comment) + " ${e.message}");
                    }
                };

                _lastClickTime = now
            }
        }
    }

    /**
     * [parent] is set when this view posts replies to an existing comment,
     * and null when it posts top-level comments attributed to [contextUrl].
     */
    fun setContext(contextUrl: String?, parent: PolycentricPlatformComment?) {
        _contextUrl = contextUrl;
        _parent = parent;
    }

    companion object {
        const val TAG = "AddCommentView"
    }
}
