package com.futo.platformplayer.views.pills

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event3
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.views.LoaderView
import com.futo.polycentric.core.ProcessHandle

data class OnLikeDislikeUpdatedArgs(
    val processHandle: ProcessHandle,
    val likes: Long,
    val hasLiked: Boolean,
    val dislikes: Long,
    val hasDisliked: Boolean,
);

class PillRatingLikesDislikes : LinearLayout {
    private val _textLikes: TextView;
    private val _textDislikes: TextView;
    private val _loaderViewLikes: LoaderView;
    private val _loaderViewDislikes: LoaderView;
    private val _seperator: View;
    private val _iconLikes: ImageView;
    private val _iconDislikes: ImageView;
    private var _isLoading: Boolean = false;

    private var _likes = 0L;
    private var _hasLiked = false;
    private var _dislikes = 0L;
    private var _hasDisliked = false;

    val onLikeDislikeUpdated = Event1<OnLikeDislikeUpdatedArgs>();

    constructor(context : Context, attrs : AttributeSet?) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.rating_likesdislikes, this, true);
        _textLikes = findViewById(R.id.pill_likes);
        _textDislikes = findViewById(R.id.pill_dislikes);
        _seperator = findViewById(R.id.pill_seperator);
        _iconDislikes = findViewById(R.id.pill_dislike_icon);
        _iconLikes = findViewById(R.id.pill_like_icon);
        _loaderViewLikes = findViewById(R.id.loader_likes)
        _loaderViewDislikes = findViewById(R.id.loader_dislikes)

        findViewById<LinearLayout>(R.id.layout_like).setOnClickListener { if (!_isLoading) StatePolycentric.instance.requireLogin(context, context.getString(R.string.please_login_to_like)) { like(it) }; };
        findViewById<LinearLayout>(R.id.layout_dislike).setOnClickListener { if (!_isLoading) StatePolycentric.instance.requireLogin(context, context.getString(R.string.please_login_to_dislike)) { dislike(it) }; };
    }

    fun setLoading(loading: Boolean) {
        if (_isLoading == loading) {
            return
        }

        if (loading) {
            _textLikes.visibility = View.GONE
            _loaderViewLikes.visibility = View.VISIBLE
            _textDislikes.visibility = View.GONE
            _loaderViewDislikes.visibility = View.VISIBLE
            _loaderViewLikes.start()
            _loaderViewDislikes.start()
        } else {
            _loaderViewLikes.stop()
            _loaderViewDislikes.stop()
            _textLikes.visibility = View.VISIBLE
            _loaderViewLikes.visibility = View.GONE
            _textDislikes.visibility = View.VISIBLE
            _loaderViewDislikes.visibility = View.GONE
        }

        _isLoading = loading
    }

    fun setRating(rating: IRating, hasLiked: Boolean = false, hasDisliked: Boolean = false) {
        setLoading(false)

        when (rating) {
            is RatingLikeDislikes -> {
                setRating(rating, hasLiked, hasDisliked);
            }
            is RatingLikes -> {
                setRating(rating, hasLiked);
            }
            else -> {
                throw Exception("Unknown rating type");
            }
        }
    }

    fun setRating(rating: RatingLikeDislikes, hasLiked: Boolean = false, hasDisliked: Boolean = false) {
        setLoading(false)

        _textLikes.text = rating.likes.toHumanNumber();
        _textDislikes.text = rating.dislikes.toHumanNumber();
        _textLikes.visibility = View.VISIBLE;
        _textDislikes.visibility = View.VISIBLE;
        _seperator.visibility = View.VISIBLE;
        _iconDislikes.visibility = View.VISIBLE;
        _likes = rating.likes;
        _dislikes = rating.dislikes;
        _hasLiked = hasLiked;
        _hasDisliked = hasDisliked;
        updateColors();
    }
    fun setRating(rating: RatingLikes, hasLiked: Boolean = false) {
        setLoading(false)

        _textLikes.text = rating.likes.toHumanNumber();
        _textLikes.visibility = View.VISIBLE;
        _textDislikes.visibility = View.GONE;
        _seperator.visibility = View.GONE;
        _iconDislikes.visibility = View.GONE;
        _likes = rating.likes;
        _dislikes = 0;
        _hasLiked = hasLiked;
        _hasDisliked = false;
        updateColors();
    }

    fun like(processHandle: ProcessHandle) {
        if (_hasDisliked) {
            _dislikes--;
            _hasDisliked = false;
            _textDislikes.text = _dislikes.toHumanNumber();
        }

        if (_hasLiked) {
            _likes--;
            _hasLiked = false;
        } else {
            _likes++;
            _hasLiked = true;
        }

        _textLikes.text = _likes.toHumanNumber();
        updateColors();
        onLikeDislikeUpdated.emit(OnLikeDislikeUpdatedArgs(processHandle, _likes, _hasLiked, _dislikes, _hasDisliked));
    }

    fun dislike(processHandle: ProcessHandle) {
        if (_hasLiked) {
            _likes--;
            _hasLiked = false;
            _textLikes.text = _likes.toHumanNumber();
        }

        if (_hasDisliked) {
            _dislikes--;
            _hasDisliked = false;
        } else {
            _dislikes++;
            _hasDisliked = true;
        }

        _textDislikes.text = _dislikes.toHumanNumber();
        updateColors();
        onLikeDislikeUpdated.emit(OnLikeDislikeUpdatedArgs(processHandle, _likes, _hasLiked, _dislikes, _hasDisliked));
    }

    private fun updateColors() {
        if (_hasLiked) {
            _textLikes.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary));
            _iconLikes.setColorFilter(ContextCompat.getColor(context, R.color.colorPrimary));
        } else {
            _textLikes.setTextColor(ContextCompat.getColor(context, R.color.white));
            _iconLikes.setColorFilter(ContextCompat.getColor(context, R.color.white));
        }

        if (_hasDisliked) {
            _textDislikes.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary));
            _iconDislikes.setColorFilter(ContextCompat.getColor(context, R.color.colorPrimary));
        } else {
            _textDislikes.setTextColor(ContextCompat.getColor(context, R.color.white));
            _iconDislikes.setColorFilter(ContextCompat.getColor(context, R.color.white));
        }
    }
}