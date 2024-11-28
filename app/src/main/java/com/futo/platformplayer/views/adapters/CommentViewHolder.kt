package com.futo.platformplayer.views.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.LazyComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.fixHtmlLinks
import com.futo.platformplayer.fullyBackfillServersAnnounceExceptions
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.setPlatformPlayerLinkMovementMethod
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.views.LoaderView
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.pills.PillButton
import com.futo.platformplayer.views.pills.PillRatingLikesDislikes
import com.futo.polycentric.core.Opinion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CommentViewHolder : ViewHolder {
    private val _creatorThumbnail: CreatorThumbnail;
    private val _textAuthor: TextView;
    private val _textMetadata: TextView;
    private val _textBody: TextView;
    private val _imageLikeIcon: ImageView;
    private val _textLikes: TextView;
    private val _imageDislikeIcon: ImageView;
    private val _textDislikes: TextView;
    private val _buttonReplies: PillButton;
    private val _layoutRating: LinearLayout;
    private val _pillRatingLikesDislikes: PillRatingLikesDislikes;
    private val _layoutComment: ConstraintLayout;
    private val _buttonDelete: FrameLayout;

    private val _containerComments: ConstraintLayout;
    private val _loader: LoaderView;

    var onRepliesClick = Event1<IPlatformComment>();
    var onDelete = Event1<IPlatformComment>();
    var onAuthorClick = Event1<IPlatformComment>();
    var comment: IPlatformComment? = null
        private set;

    constructor(viewGroup: ViewGroup) : super(LayoutInflater.from(viewGroup.context).inflate(R.layout.list_comment, viewGroup, false)) {
        _layoutComment = itemView.findViewById(R.id.layout_comment);
        _creatorThumbnail = itemView.findViewById(R.id.image_thumbnail);
        _textAuthor = itemView.findViewById(R.id.text_author);
        _textMetadata = itemView.findViewById(R.id.text_metadata);
        _textBody = itemView.findViewById(R.id.text_body);
        _imageLikeIcon = itemView.findViewById(R.id.image_like_icon);
        _textLikes = itemView.findViewById(R.id.text_likes);
        _imageDislikeIcon = itemView.findViewById(R.id.image_dislike_icon);
        _textDislikes = itemView.findViewById(R.id.text_dislikes);
        _buttonReplies = itemView.findViewById(R.id.button_replies);
        _layoutRating = itemView.findViewById(R.id.layout_rating);
        _pillRatingLikesDislikes = itemView.findViewById(R.id.rating);
        _buttonDelete = itemView.findViewById(R.id.button_delete);

        _containerComments = itemView.findViewById(R.id.comment_container);
        _loader = itemView.findViewById(R.id.loader);

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

        _creatorThumbnail.onClick.subscribe {
            val c = comment ?: return@subscribe;
            onAuthorClick.emit(c);
        }

        _creatorThumbnail.setOnClickListener {
            val c = comment ?: return@setOnClickListener;
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

        _textBody.setPlatformPlayerLinkMovementMethod(viewGroup.context);
    }

    fun bind(comment: IPlatformComment, readonly: Boolean) {

        if(comment is LazyComment){
            if(comment.isAvailable)
            {
                comment.getUnderlyingComment()?.let {
                    bind(it, readonly);
                }
                return;
            }
            else {
                _loader.visibility = View.VISIBLE;
                _loader.start();
                _containerComments.visibility = View.GONE;
                comment.setUIHandler {
                    StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                        if (it.isAvailable && it == this@CommentViewHolder.comment)
                            bind(it, readonly);
                    }
                }
            }
        }
        else {
            _loader.stop();
            _loader.visibility = View.GONE;
            _containerComments.visibility = View.VISIBLE;
        }

        _creatorThumbnail.setThumbnail(comment.author.thumbnail, false);
        val polycentricComment = if (comment is PolycentricPlatformComment) comment else null
        _creatorThumbnail.setHarborAvailable(polycentricComment != null,false, polycentricComment?.eventPointer?.system?.toProto());
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

        if (readonly) {
            _layoutRating.visibility = View.VISIBLE;
            _pillRatingLikesDislikes.visibility = View.GONE;

            when (comment.rating) {
                is RatingLikeDislikes -> {
                    val r = comment.rating as RatingLikeDislikes;
                    _textLikes.visibility = View.VISIBLE;
                    _imageLikeIcon.visibility = View.VISIBLE;
                    _textLikes.text = r.likes.toHumanNumber();

                    _imageDislikeIcon.visibility = View.VISIBLE;
                    _textDislikes.visibility = View.VISIBLE;
                    _textDislikes.text = r.dislikes.toHumanNumber();
                }
                is RatingLikes -> {
                    val r = comment.rating as RatingLikes;
                    _textLikes.visibility = View.VISIBLE;
                    _imageLikeIcon.visibility = View.VISIBLE;
                    _textLikes.text = r.likes.toHumanNumber();

                    _imageDislikeIcon.visibility = View.GONE;
                    _textDislikes.visibility = View.GONE;
                }
                else -> {
                    _textLikes.visibility = View.GONE;
                    _imageLikeIcon.visibility = View.GONE;
                    _imageDislikeIcon.visibility = View.GONE;
                    _textDislikes.visibility = View.GONE;
                }
            }
        } else {
            _layoutRating.visibility = View.GONE;
            _pillRatingLikesDislikes.visibility = View.VISIBLE;

            if (comment is PolycentricPlatformComment) {
                val hasLiked = StatePolycentric.instance.hasLiked(comment.reference.toByteArray());
                val hasDisliked = StatePolycentric.instance.hasDisliked(comment.reference.toByteArray());
                _pillRatingLikesDislikes.setRating(comment.rating, hasLiked, hasDisliked);
            } else {
                _pillRatingLikesDislikes.setRating(comment.rating);
            }
        }

        val replies = comment.replyCount ?: 0;
        if (!readonly || replies > 0) {
            _buttonReplies.visibility = View.VISIBLE;
            _buttonReplies.text.text = "$replies " + itemView.context.getString(R.string.replies);
        } else {
            _buttonReplies.visibility = View.GONE;
        }

        val processHandle = StatePolycentric.instance.processHandle
        if (processHandle != null && comment is PolycentricPlatformComment && processHandle.system == comment.eventPointer.system) {
            _buttonDelete.visibility = View.VISIBLE
        } else {
            _buttonDelete.visibility = View.GONE
        }

        this.comment = comment;
    }

    companion object {
        private const val TAG = "CommentViewHolder";
    }
}