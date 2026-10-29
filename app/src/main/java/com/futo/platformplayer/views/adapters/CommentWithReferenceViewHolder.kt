package com.futo.platformplayer.views.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.futo.platformplayer.R
import com.futo.platformplayer.polycentric.showHarborErrorToast
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.fixHtmlLinks
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.setPlatformPlayerLinkMovementMethod
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.views.ModerationWarnView
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.pills.PillButton
import com.futo.platformplayer.views.pills.PillRatingLikesDislikes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CommentWithReferenceViewHolder : ViewHolder {
    private val _creatorThumbnail: CreatorThumbnail;
    private val _textAuthor: TextView;
    private val _textMetadata: TextView;
    private val _textBody: TextView;
    private val _buttonReplies: PillButton;
    private val _pillRatingLikesDislikes: PillRatingLikesDislikes;
    private val _layoutComment: ConstraintLayout;
    private val _buttonDelete: FrameLayout;
    private val _moderationWarn: ModerationWarnView;

    val onRepliesClick = Event1<IPlatformComment>();
    val onDelete = Event1<IPlatformComment>();
    val onClick = Event1<IPlatformComment>();
    val onAuthorClick = Event1<IPlatformComment>();
    var comment: IPlatformComment? = null
        private set;

    constructor(viewGroup: ViewGroup) : super(LayoutInflater.from(viewGroup.context).inflate(R.layout.list_comment_with_reference, viewGroup, false)) {
        _layoutComment = itemView.findViewById(R.id.layout_comment);
        _creatorThumbnail = itemView.findViewById(R.id.image_thumbnail);
        _textAuthor = itemView.findViewById(R.id.text_author);
        _textMetadata = itemView.findViewById(R.id.text_metadata);
        _textBody = itemView.findViewById(R.id.text_body);
        _buttonReplies = itemView.findViewById(R.id.button_replies);
        _pillRatingLikesDislikes = itemView.findViewById(R.id.rating);
        _buttonDelete = itemView.findViewById(R.id.button_delete)
        _moderationWarn = itemView.findViewById(R.id.moderation_warn)

        _pillRatingLikesDislikes.onLikeDislikeUpdated.subscribe { args ->
            val c = comment
            if (c !is PolycentricPlatformComment) {
                throw Exception("Not implemented for non polycentric comments")
            }

            _layoutComment.alpha = if (args.dislikes > 2 && args.dislikes.toFloat() / (args.likes + args.dislikes).toFloat() >= 0.7f) 0.5f else 1.0f;

            StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                try {
                    StatePolycentric.instance.setCommentRating(
                        c,
                        if (args.hasLiked) true else if (args.hasDisliked) false else null,
                    )
                } catch (e: Throwable) {
                    Logger.w(TAG, "Failed to set comment rating.", e)
                    showHarborErrorToast(itemView.context.getString(R.string.failed_to_set_rating), e)
                }
            }
        };

        _creatorThumbnail.onClick.subscribe {
            val c = comment ?: return@subscribe;
            onAuthorClick.emit(c);
        }
        _textAuthor.setOnClickListener {
            val c = comment ?: return@setOnClickListener;
            onAuthorClick.emit(c);
        }
        _buttonReplies.onClick.subscribe {
            val c = comment ?: return@subscribe;
            onRepliesClick.emit(c);
        }

        _buttonDelete.setOnClickListener {
            val c = comment ?: return@setOnClickListener;
            onDelete.emit(c);
        }

        _layoutComment.setOnClickListener {
            val c = comment ?: return@setOnClickListener;
            onClick.emit(c);
        }

        _moderationWarn.onShow.subscribe {
            val c = comment ?: return@subscribe;
            bind(c);
        }

        _textBody.setPlatformPlayerLinkMovementMethod(viewGroup.context);
    }

    fun bind(comment: IPlatformComment) {
        _creatorThumbnail.setThumbnail(comment.author.thumbnail, false);
        val polycentricComment = if (comment is PolycentricPlatformComment) comment else null
        _creatorThumbnail.setHarborAvailable(polycentricComment != null, false, polycentricComment?.key?.identity);
        _textAuthor.text = comment.author.name;

        val date = comment.date;
        if (date != null) {
            _textMetadata.visibility = View.VISIBLE;
            _textMetadata.text = " • ${date.toHumanNowDiffString()} ago";
        } else {
            _textMetadata.visibility = View.GONE;
        }

        val rating = comment.rating;
        if (rating is RatingLikeDislikes) {
            _layoutComment.alpha = if (Settings.instance.comments.badReputationCommentsFading &&
                rating.dislikes > 2 && rating.dislikes.toFloat() / (rating.likes + rating.dislikes).toFloat() >= 0.7f) 0.5f else 1.0f;
        } else {
            _layoutComment.alpha = 1.0f;
        }

        _textBody.text = comment.message.fixHtmlLinks();

        this.comment = comment;

        if (comment is PolycentricPlatformComment) {
            val mine = StatePolycentric.instance.myCommentRating(comment);
            _pillRatingLikesDislikes.setRating(rating, mine == true, mine == false);
            _pillRatingLikesDislikes.visibility = View.VISIBLE

            _buttonReplies.setLoading(false)
            _buttonReplies.visibility = View.VISIBLE;
            val replies = comment.replyCount ?: 0;
            _buttonReplies.text.text = replies.toString();
        } else {
            _pillRatingLikesDislikes.visibility = View.GONE
            _buttonReplies.visibility = View.GONE
        }

        // A warned comment hides the content until the warning is dismissed
        if (_moderationWarn.bind(comment)) {
            _textBody.visibility = View.GONE
            _pillRatingLikesDislikes.visibility = View.GONE
            _buttonReplies.visibility = View.GONE
            _buttonDelete.visibility = View.GONE
        } else {
            _textBody.visibility = View.VISIBLE
            _buttonDelete.visibility = View.VISIBLE
        }
    }

    companion object {
        private const val TAG = "CommentWithReferenceViewHolder";
    }
}
