package com.futo.platformplayer.views.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.models.chapters.ChapterType
import com.futo.platformplayer.api.media.models.chapters.IChapter
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.LazyComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.fixHtmlLinks
import com.futo.platformplayer.setPlatformPlayerLinkMovementMethod
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.toHumanDuration
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.toHumanTime
import com.futo.platformplayer.views.LoaderView
import com.futo.platformplayer.views.others.CreatorThumbnail
import com.futo.platformplayer.views.pills.PillButton
import com.futo.platformplayer.views.pills.PillRatingLikesDislikes
import com.futo.polycentric.core.ApiMethods
import com.futo.polycentric.core.Opinion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChapterViewHolder : ViewHolder {

    private val _layoutChapter: ConstraintLayout;

    private val _containerChapter: ConstraintLayout;

    private val _textTitle: TextView;
    private val _textTimestamp: TextView;
    private val _textMeta: TextView;

    var onClick = Event1<IChapter>();
    var chapter: IChapter? = null
        private set;

    constructor(viewGroup: ViewGroup) : super(LayoutInflater.from(viewGroup.context).inflate(R.layout.list_chapter, viewGroup, false)) {
        _layoutChapter = itemView.findViewById(R.id.layout_chapter);
        _containerChapter = itemView.findViewById(R.id.chapter_container);

        _containerChapter.setOnClickListener {
            chapter?.let {
                onClick.emit(it);
            }
        }

        _textTitle = itemView.findViewById(R.id.text_title);
        _textTimestamp = itemView.findViewById(R.id.text_timestamp);
        _textMeta = itemView.findViewById(R.id.text_meta);
    }

    fun bind(chapter: IChapter) {
        _textTitle.text = chapter.name;
        _textTimestamp.text = chapter.timeStart.toLong().toHumanTime(false);

        if(chapter.type == ChapterType.NORMAL) {
            _textMeta.isVisible = false;
        }
        else {
            _textMeta.isVisible = true;
            when(chapter.type) {
                ChapterType.SKIP -> _textMeta.text = "(Skip)";
                ChapterType.SKIPPABLE -> _textMeta.text = "(Manual Skip)"
                ChapterType.SKIPONCE -> _textMeta.text = "(Skip Once)"
                else -> _textMeta.isVisible = false;
            };
        }
        this.chapter = chapter;
    }

    companion object {
        private const val TAG = "CommentViewHolder";
    }
}