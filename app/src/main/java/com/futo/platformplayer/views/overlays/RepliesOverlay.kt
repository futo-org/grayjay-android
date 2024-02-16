package com.futo.platformplayer.views.overlays

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.http.ManagedHttpClient
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import userpackage.Protocol

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
    private val _client = ManagedHttpClient()
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
                load(false, metadata, c.contextUrl, c.reference, c, { StatePolycentric.instance.getCommentPager(c.contextUrl, c.reference) });
            } else {
                load(true, metadata, null, null, c, { StatePlatform.instance.getSubComments(c) });
            }
        };

        _layoutParentComment.setOnClickListener {
            val p = _parentComment
            if (p !is PolycentricPlatformComment) {
                return@setOnClickListener
            }

            val ref = p.parentReference ?: return@setOnClickListener
            handleParentClick(p.contextUrl, ref)
        }

        _topbar.onClose.subscribe(this, onClose::emit);
        _topbar.setInfo(context.getString(R.string.Replies), "");
    }

    fun load(readonly: Boolean, metadata: String, contextUrl: String?, ref: Protocol.Reference?, parentComment: IPlatformComment? = null, loader: suspend () -> IPager<IPlatformComment>, onCommentAdded: ((comment: IPlatformComment) -> Unit)? = null, onParentClick: ((comment: IPlatformComment) -> Unit)? = null) {
        _readonly = readonly;
        if (readonly) {
            _addCommentView.visibility = View.GONE;
        } else {
            _addCommentView.visibility = View.VISIBLE;
            _addCommentView.setContext(contextUrl, ref);
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

    fun handleParentClick(contextUrl: String, ref: Protocol.Reference): Boolean {
        val ctx = context
        if (ctx !is MainActivity) {
            return false
        }

        return when (ref.referenceType) {
            2L -> {
                setLoading(true)

                StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                    try {
                        val parentComment = StatePolycentric.instance.getComment(contextUrl, ref)
                        val replyCount = parentComment.replyCount ?: 0;
                        var metadata = "";
                        if (replyCount > 0) {
                            metadata += "$replyCount " + context.getString(R.string.replies);
                        }

                        withContext(Dispatchers.Main) {
                            setLoading(false)

                            load(false, metadata, parentComment.contextUrl, parentComment.reference, parentComment,
                                { StatePolycentric.instance.getCommentPager(contextUrl, ref) })
                        }
                    } catch (e: Throwable) {
                        withContext(Dispatchers.Main) {
                            setLoading(false)
                        }

                        Logger.e(TAG, "Failed to load parent comment.", e)
                        UIDialogs.toast("Failed to load comment")
                    }
                }

                true
            }
            3L -> {
                StateApp.instance.scopeOrNull?.launch {
                    try {
                        val url = referenceToUrl(_client, ref) ?: return@launch
                        withContext(Dispatchers.Main) {
                            ctx.handleUrl(url)
                            onClose.emit()
                        }
                    } catch (e: Throwable) {
                        Logger.i(TAG, "Failed to open ref.", e)
                    }
                }

                false
            }
            else -> false
        }
    }

    private fun referenceToUrl(client: ManagedHttpClient, parentRef: Protocol.Reference): String? {
        val refBytes = parentRef.reference?.toByteArray() ?: return null
        val ref = refBytes.decodeToString()

        try {
            Uri.parse(ref)
            return ref
        } catch (e: Throwable) {
            try {
                return oldReferenceToUrl(client, ref)
            } catch (f: Throwable) {
                Logger.i(TAG, "Failed to handle URL.", f)
            }
        }

        return null
    }

    private fun oldReferenceToUrl(client: ManagedHttpClient, reference: String): String? {
        return when {
            reference.startsWith("video_episode:") -> {
                val response = client.get("https://content.api.nebula.app/video_episodes/$reference")
                if (!response.isOk) {
                    throw Exception("Failed to resolve nebula video (${response.code}).")
                }

                val respString = response.body?.string()
                val jsonElement = respString?.let { Json.parseToJsonElement(it) }
                return jsonElement?.jsonObject?.get("share_url")?.jsonPrimitive?.content
            }

            reference.length == 11 -> "https://www.youtube.com/watch?v=$reference"

            reference.length == 40 -> {
                val response = client.post("https://api.na-backend.odysee.com/api/v1/proxy?m=claim_search", hashMapOf(
                    "Content-Type" to "application/json"
                ))

                if (!response.isOk) {
                    throw Exception("Failed to resolve claim (${response.code}).")
                }

                val jsonElement = response.body?.string()?.let { Json.parseToJsonElement(it) }
                val canonicalUrl = jsonElement?.jsonObject?.get("result")
                    ?.jsonObject?.get("items")
                    ?.jsonArray?.get(0)
                    ?.jsonObject?.get("canonical_url")
                    ?.jsonPrimitive?.content

                canonicalUrl ?: throw Exception("Failed to get canonical URL.")
            }

            reference.startsWith("v") && (reference.length == 7 || reference.length == 6) -> "https://rumble.com/$reference"

            Regex("^\\d+\$").matches(reference) -> "https://www.twitch.tv/videos/$reference"

            else -> null
        }
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