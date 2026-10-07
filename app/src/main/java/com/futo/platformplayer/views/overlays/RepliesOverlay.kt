package com.futo.platformplayer.views.overlays

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.fixHtmlLinks
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.views.behavior.NonScrollingTextView
import com.futo.platformplayer.views.comments.AddCommentView
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.segments.CommentsList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RepliesOverlay : LinearLayout {
    val onClose = Event0();

    private val _topbar: OverlayTopbar;
    private val _commentsList: CommentsList;
    private val _addCommentView: AddCommentView;
    private val _textBody: NonScrollingTextView;
    private val _textAuthor: TextView;
    private val _textMetadata: TextView;
    private val _creatorThumbnail: CreatorThumbnail;
    private val _layoutParentComment: ConstraintLayout;
    private var _readonly = false;
    private var _loading = true;
    private var _parentComment: IPlatformComment? = null;
    private var _onCommentAdded: ((comment: IPlatformComment) -> Unit)? = null;
    private val _loaderOverlay: LoaderOverlay
    private val _layoutItems: LinearLayout

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        inflate(context, R.layout.overlay_replies, this)
        _layoutItems = findViewById(R.id.layout_items)
        _topbar = findViewById(R.id.topbar);
        _commentsList = findViewById(R.id.comments_list);
        _addCommentView = findViewById(R.id.add_comment_view);
        _textBody = findViewById(R.id.text_body)
        _textMetadata = findViewById(R.id.text_metadata)
        _textAuthor = findViewById(R.id.text_author)
        _creatorThumbnail = findViewById(R.id.image_thumbnail)
        _layoutParentComment = findViewById(R.id.layout_parent_comment)
        _loaderOverlay = findViewById(R.id.loader_overlay)
        setLoading(false);

        _layoutItems.removeView(_layoutParentComment)
        _commentsList.setPrependedView(_layoutParentComment)

        _addCommentView.onCommentAdded.subscribe {
            _commentsList.addComment(it);
            _onCommentAdded?.invoke(it);
        }

        _commentsList.onCommentsLoaded.subscribe { count ->
            if (_readonly && count == 0) {
                UIDialogs.toast(context, context.getString(R.string.expected_at_least_one_reply_but_no_replies_were_returned_by_the_server));
            }
        }

        _commentsList.onRepliesClick.subscribe { c ->
            val replyCount = c.replyCount;
            var metadata = "";
            if (replyCount != null && replyCount > 0) {
                metadata += "$replyCount " + context.getString(R.string.replies);
            }

            if (c is PolycentricPlatformComment) {
                load(false, metadata, c.contextUrl, c, { StatePolycentric.instance.getReplies(c) });
            } else {
                load(true, metadata, null, c, { StatePlatform.instance.getSubComments(c) });
            }
        };

        _layoutParentComment.setOnClickListener {
            val p = _parentComment
            if (p !is PolycentricPlatformComment) {
                return@setOnClickListener
            }

            handleParentClick(p)
        }

        _topbar.onClose.subscribe(this, onClose::emit);
    }


    fun load(readonly: Boolean, metadata: String, contextUrl: String?, parentComment: IPlatformComment? = null, loader: suspend () -> IPager<IPlatformComment>, onCommentAdded: ((comment: IPlatformComment) -> Unit)? = null, onParentClick: ((comment: IPlatformComment) -> Unit)? = null) {
        _readonly = readonly;
        if (readonly) {
            _addCommentView.visibility = View.GONE;
        } else {
            _addCommentView.visibility = View.VISIBLE;
            _addCommentView.setContext(contextUrl, parentComment as? PolycentricPlatformComment);
        }

        if (parentComment == null) {
            _layoutParentComment.visibility = View.GONE
        } else {
            _layoutParentComment.visibility = View.VISIBLE

            _textBody.text = parentComment.message.fixHtmlLinks()
            _textAuthor.text = parentComment.author.name

            val date = parentComment.date
            if (date != null) {
                _textMetadata.visibility = View.VISIBLE
                _textMetadata.text = " • ${date.toHumanNowDiffString()} ago"
            } else {
                _textMetadata.visibility = View.GONE
            }

            _creatorThumbnail.setThumbnail(parentComment.author.thumbnail, false);
            val polycentricPlatformComment = if (parentComment is PolycentricPlatformComment) parentComment else null
            _creatorThumbnail.setHarborAvailable(polycentricPlatformComment != null,false, polycentricPlatformComment?.eventPointer?.system?.toProto());
        }

        _topbar.setInfo(context.getString(R.string.Replies), metadata);
        _commentsList.load(readonly, loader);
        _onCommentAdded = onCommentAdded;
        _parentComment = parentComment;
    }

    /**
     * Navigate to the parent of [parentComment] (if present) such that the whole
     * reply chain is one level higher.
     */
    fun handleParentClick(parentComment: PolycentricPlatformComment): Boolean {
        val ctx = context
        if (ctx !is MainActivity) {
            return false
        }

        val parentKey = parentComment.parent ?: return false
        setLoading(true)

        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
            try {
                val parent = StatePolycentric.instance.getComment(parentKey)
                    ?: throw IllegalStateException("Comment not found.")

                val replyCount = parent.replyCount ?: 0;
                var metadata = "";
                if (replyCount > 0) {
                    metadata += "$replyCount " + context.getString(R.string.replies);
                }

                withContext(Dispatchers.Main) {
                    setLoading(false)

                    load(false, metadata, parent.contextUrl, parent, { StatePolycentric.instance.getReplies(parent) })
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                }

                Logger.e(TAG, "Failed to load parent comment.", e)
                UIDialogs.toast("Failed to load comment")
            }
        }

        return true
    }

    private fun setLoading(loading: Boolean) {
        if (_loading == loading) {
            return;
        }

        _loading = loading;
        if (!loading) {
            _loaderOverlay.hide()
        } else {
            _loaderOverlay.show()
        }
    }

    fun cleanup() {
        _topbar.onClose.remove(this);
        _onCommentAdded = null;
        _commentsList.cancel();
    }

    companion object {
        private const val TAG = "RepliesOverlay"
    }
}
