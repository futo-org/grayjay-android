package com.futo.platformplayer.views.overlays

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.views.comments.AddCommentView
import com.futo.platformplayer.views.segments.CommentsList
import userpackage.Protocol

class RepliesOverlay : LinearLayout {
    val onClose = Event0();

    private val _topbar: OverlayTopbar;
    private val _commentsList: CommentsList;
    private val _addCommentView: AddCommentView;
    private var _readonly = false;
    private var _onCommentAdded: ((comment: IPlatformComment) -> Unit)? = null;

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.overlay_replies, this)
        _topbar = findViewById(R.id.topbar);
        _commentsList = findViewById(R.id.comments_list);
        _addCommentView = findViewById(R.id.add_comment_view);

        _addCommentView.onCommentAdded.subscribe {
            _commentsList.addComment(it);
            _onCommentAdded?.invoke(it);
        }

        _commentsList.onCommentsLoaded.subscribe { count ->
            if (_readonly && count == 0) {
                UIDialogs.toast(context, "Expected at least one reply but no replies were returned by the server");
            }
        }

        _commentsList.onClick.subscribe { c ->
            val replyCount = c.replyCount;
            var metadata = "";
            if (replyCount != null && replyCount > 0) {
                metadata += "$replyCount replies";
            }

            if (c is PolycentricPlatformComment) {
                load(false, metadata, c.contextUrl, c.reference, { StatePolycentric.instance.getCommentPager(c.contextUrl, c.reference) });
            } else {
                load(true, metadata, null, null, { StatePlatform.instance.getSubComments(c) });
            }
        };

        _topbar.onClose.subscribe(this, onClose::emit);
        _topbar.setInfo("Replies", "");
    }

    fun load(readonly: Boolean, metadata: String, contextUrl: String?, ref: Protocol.Reference?, loader: suspend () -> IPager<IPlatformComment>, onCommentAdded: ((comment: IPlatformComment) -> Unit)? = null) {
        _readonly = readonly;
        if (readonly) {
            _addCommentView.visibility = View.GONE;
        } else {
            _addCommentView.visibility = View.VISIBLE;
            _addCommentView.setContext(contextUrl, ref);
        }

        _topbar.setInfo("Replies", metadata);
        _commentsList.load(readonly, loader);
        _onCommentAdded = onCommentAdded;
    }

    fun cleanup() {
        _topbar.onClose.remove(this);
        _onCommentAdded = null;
        _commentsList.cancel();
    }
}