package com.futo.platformplayer.views.segments

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.chapters.IChapter
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.LazyComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.structures.IAsyncPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.engine.exceptions.ScriptUnavailableException
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePolycentric
import com.futo.platformplayer.views.adapters.ChapterViewHolder
import com.futo.platformplayer.views.adapters.CommentViewHolder
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import com.futo.polycentric.core.fullyBackfillServersAnnounceExceptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.UnknownHostException

class ChaptersList : ConstraintLayout {
    private val _llmReplies: LinearLayoutManager;

    private val _adapterChapters: InsertedViewAdapterWithLoader<ChapterViewHolder>;
    private val _recyclerChapters: RecyclerView;
    private val _chapters: ArrayList<IChapter> = arrayListOf();
    private val _prependedView: FrameLayout;
    private var _readonly: Boolean = false;
    private val _layoutScrollToTop: FrameLayout;

    var onChapterClick = Event1<IChapter>();
    var onCommentsLoaded = Event1<Int>();

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_chapters_list, this, true);

        _recyclerChapters = findViewById(R.id.recycler_chapters);

        _layoutScrollToTop = findViewById(R.id.layout_scroll_to_top);
        _layoutScrollToTop.setOnClickListener {
            _recyclerChapters.smoothScrollToPosition(0)
        }
        _layoutScrollToTop.visibility = View.GONE

        _prependedView = FrameLayout(context);
        _prependedView.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);

        _adapterChapters = InsertedViewAdapterWithLoader(context, arrayListOf(_prependedView), arrayListOf(),
            childCountGetter = { _chapters.size },
            childViewHolderBinder = { viewHolder, position -> viewHolder.bind(_chapters[position]); },
            childViewHolderFactory = { viewGroup, _ ->
                val holder = ChapterViewHolder(viewGroup);
                holder.onClick.subscribe { c -> onChapterClick.emit(c) };
                return@InsertedViewAdapterWithLoader holder;
            }
        );

        _llmReplies = LinearLayoutManager(context);
        _recyclerChapters.layoutManager = _llmReplies;
        _recyclerChapters.adapter = _adapterChapters;
    }

    fun addChapter(chapter: IChapter) {
        _chapters.add(0, chapter);
        _adapterChapters.notifyItemRangeInserted(_adapterChapters.childToParentPosition(0), 1);
    }

    fun setPrependedView(view: View) {
        _prependedView.removeAllViews();
        _prependedView.addView(view);
    }

    fun setChapters(chapters: List<IChapter>) {
        _chapters.clear();
        _chapters.addAll(chapters);
        _adapterChapters.notifyDataSetChanged();
    }

    fun clear() {
        _chapters.clear();
        _adapterChapters.notifyDataSetChanged();
    }

    companion object {
        private const val TAG = "CommentsList";
    }
}