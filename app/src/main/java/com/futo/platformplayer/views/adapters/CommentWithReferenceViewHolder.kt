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
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.fixHtmlLinks
import com.futo.platformplayer.fullyBackfillServersAnnounceExceptions
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.setPlatformPlayerLinkMovementMethod
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.pills.PillButton
import com.futo.platformplayer.views.pills.PillRatingLikesDislikes
import com.futo.polycentric.core.Opinion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.IdentityHashMap

class CommentWithReferenceViewHolder : ViewHolder {
    private val _creatorThumbnail: CreatorThumbnail;
    private val _textAuthor: TextView;
    private val _textMetadata: TextView;
    private val _textBody: TextView;
    private val _buttonReplies: PillButton;
    private val _pillRatingLikesDislikes: PillRatingLikesDislikes;
    private val _layoutComment: ConstraintLayout;
    private val _buttonDelete: FrameLayout;
    private val _cache: IdentityHashMap<IPlatformComment, StatePolycentric.LikesDislikesReplies>;
    private var _likesDislikesReplies: StatePolycentric.LikesDislikesReplies? = null;

    private val _taskGetLiveComment = TaskHandler(StateApp.instance.scopeGetter, ::getLikesDislikesReplies)
        .success {
            _likesDislikesReplies = it
            updateLikesDislikesReplies()
        }
        .exception<Throwable> {
            Logger.w(TAG, "Failed to get live comment.", it);
            //TODO: Show error
            hideLikesDislikesReplies()
        }

    var onRepliesClick = Event1<IPlatformComment>();
    var onDelete = Event1<IPlatformComment>();
    var comment: IPlatformComment? = null
        private set;

    constructor(viewGroup: ViewGroup, cache: IdentityHashMap<IPlatformComment, StatePolycentric.LikesDislikesReplies>) : super(LayoutInflater.from(viewGroup.context).inflate(R.layout.list_comment_with_reference, viewGroup, false)) {
        _layoutComment = itemView.findViewById(R.id.layout_comment);
        _creatorThumbnail = itemView.findViewById(R.id.image_thumbnail);
        _textAuthor = itemView.findViewById(R.id.text_author);
        _textMetadata = itemView.findViewById(R.id.text_metadata);
        _textBody = itemView.findViewById(R.id.text_body);
        _buttonReplies = itemView.findViewById(R.id.button_replies);
        _pillRatingLikesDislikes = itemView.findViewById(R.id.rating);
        _buttonDelete = itemView.findViewById(R.id.button_delete)
        _cache = cache

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

    private suspend fun getLikesDislikesReplies(c: PolycentricPlatformComment): StatePolycentric.LikesDislikesReplies {
        val likesDislikesReplies = StatePolycentric.instance.getLikesDislikesReplies(c.reference)
        synchronized(_cache) {
            _cache[c] = likesDislikesReplies
        }
        return likesDislikesReplies
    }

    fun bind(comment: IPlatformComment) {
        Log.i(TAG, "bind")

        _likesDislikesReplies = null;
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
            _layoutComment.alpha = if (Settings.instance.comments.badReputationCommentsFading &&
                rating.dislikes > 2 && rating.dislikes.toFloat() / (rating.likes + rating.dislikes).toFloat() >= 0.7f) 0.5f else 1.0f;
        } else {
            _layoutComment.alpha = 1.0f;
        }

        _textBody.text = comment.message.fixHtmlLinks();

        this.comment = comment;
        updateLikesDislikesReplies();
    }

    private fun updateLikesDislikesReplies() {
        Log.i(TAG, "updateLikesDislikesReplies")

        val c = comment ?: return
        if (c is PolycentricPlatformComment) {
            if (_likesDislikesReplies == null) {
                Log.i(TAG, "updateLikesDislikesReplies retrieving from cache")

                synchronized(_cache) {
                    _likesDislikesReplies = _cache[c]
                }
            }

            val likesDislikesReplies = _likesDislikesReplies
            if (likesDislikesReplies != null) {
                Log.i(TAG, "updateLikesDislikesReplies set")

                val hasLiked = StatePolycentric.instance.hasLiked(c.reference);
                val hasDisliked = StatePolycentric.instance.hasDisliked(c.reference);
                _pillRatingLikesDislikes.setRating(RatingLikeDislikes(likesDislikesReplies.likes, likesDislikesReplies.dislikes), hasLiked, hasDisliked);

                _buttonReplies.setLoading(false)

                val replies = likesDislikesReplies.replyCount;
                _buttonReplies.visibility = View.VISIBLE;
                _buttonReplies.text.text = "$replies " + itemView.context.getString(R.string.replies);
            } else {
                Log.i(TAG, "updateLikesDislikesReplies to load")

                _pillRatingLikesDislikes.setLoading(true)
                _buttonReplies.setLoading(true)
                _taskGetLiveComment.run(c)
            }
        } else {
            hideLikesDislikesReplies()
        }
    }

    private fun hideLikesDislikesReplies() {
        _pillRatingLikesDislikes.visibility = View.GONE
        _buttonReplies.visibility = View.GONE
    }

    companion object {
        private const val TAG = "CommentWithReferenceViewHolder";
    }
}