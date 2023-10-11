package com.futo.platformplayer.views.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
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

    var onClick = Event1<IPlatformComment>();
    var comment: IPlatformComment? = null
        private set;

    constructor(viewGroup: ViewGroup) : super(LayoutInflater.from(viewGroup.context).inflate(R.layout.list_comment, viewGroup, false)) {
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

        _pillRatingLikesDislikes.onLikeDislikeUpdated.subscribe { processHandle, hasLiked, hasDisliked ->
            val c = comment
            if (c !is PolycentricPlatformComment) {
                throw Exception("Not implemented for non polycentric comments")
            }

            if (hasLiked) {
                processHandle.opinion(c.reference, Opinion.like);
            } else if (hasDisliked) {
                processHandle.opinion(c.reference, Opinion.dislike);
            } else {
                processHandle.opinion(c.reference, Opinion.neutral);
            }

            StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                try {
                    processHandle.fullyBackfillServers();
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to backfill servers.", e)
                }
            }

            StatePolycentric.instance.updateLikeMap(c.reference, hasLiked, hasDisliked)
        };

        _buttonReplies.onClick.subscribe {
            val c = comment ?: return@subscribe;
            onClick.emit(c);
        }

        _textBody.setPlatformPlayerLinkMovementMethod(viewGroup.context);
    }

    fun bind(comment: IPlatformComment, readonly: Boolean) {
        _creatorThumbnail.setThumbnail(comment.author.thumbnail, false);
        _textAuthor.text = comment.author.name;

        val date = comment.date;
        if (date != null) {
            _textMetadata.visibility = View.VISIBLE;
            _textMetadata.text = " • ${date.toHumanNowDiffString()} ago";
        } else {
            _textMetadata.visibility = View.GONE;
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
                val hasLiked = StatePolycentric.instance.hasLiked(comment.reference);
                val hasDisliked = StatePolycentric.instance.hasDisliked(comment.reference);
                _pillRatingLikesDislikes.setRating(comment.rating, hasLiked, hasDisliked);
            } else {
                _pillRatingLikesDislikes.setRating(comment.rating);
            }
        }

        val replies = comment.replyCount ?: 0;
        if (!readonly || replies > 0) {
            _buttonReplies.visibility = View.VISIBLE;
            _buttonReplies.text.text = "$replies replies";
        } else {
            _buttonReplies.visibility = View.GONE;
        }

        this.comment = comment;
    }

    companion object {
        private const val TAG = "CommentViewHolder";
    }
}