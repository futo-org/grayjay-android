package com.futo.platformplayer.views.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.pills.PillButton
import com.futo.platformplayer.views.pills.PillRatingLikesDislikes
import com.futo.polycentric.core.Opinion
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

    private val _taskGetLiveComment = TaskHandler<PolycentricPlatformComment, PolycentricPlatformComment>(StateApp.instance.scopeGetter, { StatePolycentric.instance.getLiveComment(it.contextUrl, it.reference) })
        .success {
            bind(it, true);
        }
        .exception<Throwable> {
            Logger.w(TAG, "Failed to get live comment.", it);
            //TODO: Show error
        }

    var onRepliesClick = Event1<IPlatformComment>();
    var onDelete = Event1<IPlatformComment>();
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

        _pillRatingLikesDislikes.onLikeDislikeUpdated.subscribe { args ->
            val c = comment
            if (c !is PolycentricPlatformComment) {
                throw Exception("Not implemented for non polycentric comments")
            }

            if (args.hasLiked) {
                args.processHandle.opinion(c.reference, Opinion.like);
            } else if (args.hasDisliked) {
                args.processHandle.opinion(c.reference, Opinion.dislike);
            } else {
                args.processHandle.opinion(c.reference, Opinion.neutral);
            }

            _layoutComment.alpha = if (args.dislikes > 2 && args.dislikes.toFloat() / (args.likes + args.dislikes).toFloat() >= 0.7f) 0.5f else 1.0f;

            StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                try {
                    Logger.i(TAG, "Started backfill");
                    args.processHandle.fullyBackfillServersAnnounceExceptions();
                    Logger.i(TAG, "Finished backfill");
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to backfill servers.", e)
                }
            }

            StatePolycentric.instance.updateLikeMap(c.reference, args.hasLiked, args.hasDisliked)
        };

        _buttonReplies.onClick.subscribe {
            val c = comment ?: return@subscribe;
            onRepliesClick.emit(c);
        }

        _buttonDelete.setOnClickListener {
            val c = comment ?: return@setOnClickListener;
            onDelete.emit(c);
        }

        _textBody.setPlatformPlayerLinkMovementMethod(viewGroup.context);
    }

    fun bind(comment: IPlatformComment, live: Boolean = false) {
        _taskGetLiveComment.cancel()

        _creatorThumbnail.setThumbnail(comment.author.thumbnail, false);
        _creatorThumbnail.setHarborAvailable(comment is PolycentricPlatformComment,false);
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
            _layoutComment.alpha = if (rating.dislikes > 2 && rating.dislikes.toFloat() / (rating.likes + rating.dislikes).toFloat() >= 0.7f) 0.5f else 1.0f;
        } else {
            _layoutComment.alpha = 1.0f;
        }

        _textBody.text = comment.message.fixHtmlLinks();

        if (comment is PolycentricPlatformComment) {
            if (live) {
                val hasLiked = StatePolycentric.instance.hasLiked(comment.reference);
                val hasDisliked = StatePolycentric.instance.hasDisliked(comment.reference);
                _pillRatingLikesDislikes.setRating(comment.rating, hasLiked, hasDisliked);
            } else {
                _pillRatingLikesDislikes.setLoading(true)
            }

            if (live) {
                _buttonReplies.setLoading(false)

                val replies = comment.replyCount ?: 0;
                if (replies > 0) {
                    _buttonReplies.visibility = View.VISIBLE;
                    _buttonReplies.text.text = "$replies " + itemView.context.getString(R.string.replies);
                } else {
                    _buttonReplies.visibility = View.GONE;
                }
            } else {
                _buttonReplies.setLoading(true)
            }

            if (false) {
                //Restore from cached
            } else {
                //_taskGetLiveComment.run(comment)
            }
        } else {
            _pillRatingLikesDislikes.visibility = View.GONE
            _buttonReplies.visibility = View.GONE
        }

        this.comment = comment;
    }

    companion object {
        private const val TAG = "CommentWithReferenceViewHolder";
    }
}