package com.futo.platformplayer.views.segments

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.R
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.api.media.structures.IAsyncPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.fragment.mainactivity.main.ChannelFragment
import com.futo.platformplayer.views.adapters.CommentViewHolder
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import java.net.UnknownHostException

class CommentsList : ConstraintLayout {
    private val _llmReplies: LinearLayoutManager;
    private val _taskLoadComments = if(!isInEditMode) TaskHandler<suspend () -> IPager<IPlatformComment>, IPager<IPlatformComment>>(StateApp.instance.scopeGetter, { it(); })
        .success { pager -> onCommentsLoaded(pager); }
        .exception<UnknownHostException> {
            UIDialogs.toast("Failed to load comments");
            setLoading(false);
        }
        .exception<Throwable> {
            Logger.e(TAG, "Failed to load comments.", it);
            UIDialogs.toast(context, context.getString(R.string.failed_to_load_comments) + "\n" + (it.message ?: ""));
            //UIDialogs.showGeneralRetryErrorDialog(context, context.getString(R.string.failed_to_load_comments) + (it.message ?: ""), it, ::fetchComments);
            setLoading(false);
        } else TaskHandler(IPlatformVideoDetails::class.java, StateApp.instance.scopeGetter);

    private var _nextPageHandler: TaskHandler<IPager<IPlatformComment>, List<IPlatformComment>> = TaskHandler<IPager<IPlatformComment>, List<IPlatformComment>>(StateApp.instance.scopeGetter, {
        if (it is IAsyncPager<*>)
            it.nextPageAsync();
        else
            it.nextPage();

        return@TaskHandler it.getResults();
    }).success {
        onNextPageLoaded(it);
    }.exception<Throwable> {
        Logger.w(TAG, "Failed to load next page.", it);
        UIDialogs.showGeneralRetryErrorDialog(context, it.message ?: "", it, { loadNextPage() });
    };

    private val _scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy);
            onScrolled();
        }
    };

    private var _loader: (suspend () -> IPager<IPlatformComment>)? = null;
    private val _adapterComments: InsertedViewAdapterWithLoader<CommentViewHolder>;
    private val _recyclerComments: RecyclerView;
    private val _comments: ArrayList<IPlatformComment> = arrayListOf();
    private var _commentsPager: IPager<IPlatformComment>? = null;
    private var _loading = false;
    private val _prependedView: FrameLayout;
    private var _readonly: Boolean = false;

    var onClick = Event1<IPlatformComment>();
    var onCommentsLoaded = Event1<Int>();

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_comments_list, this, true);

        _recyclerComments = findViewById(R.id.recycler_comments);

        _prependedView = FrameLayout(context);
        _prependedView.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);

        _adapterComments = InsertedViewAdapterWithLoader(context, arrayListOf(_prependedView), arrayListOf(),
            childCountGetter = { _comments.size },
            childViewHolderBinder = { viewHolder, position -> viewHolder.bind(_comments[position], _readonly); },
            childViewHolderFactory = { viewGroup, _ ->
                val holder = CommentViewHolder(viewGroup);
                holder.onClick.subscribe { c -> onClick.emit(c) };
                return@InsertedViewAdapterWithLoader holder;
            }
        );

        _llmReplies = LinearLayoutManager(context);
        _recyclerComments.layoutManager = _llmReplies;
        _recyclerComments.adapter = _adapterComments;
        _recyclerComments.addOnScrollListener(_scrollListener);
    }

    fun addComment(comment: IPlatformComment) {
        _comments.add(0, comment);
        _adapterComments.notifyItemRangeInserted(_adapterComments.childToParentPosition(0), 1);
    }

    fun setPrependedView(view: View) {
        _prependedView.removeAllViews();
        _prependedView.addView(view);
    }

    private fun onScrolled() {
        val visibleItemCount = _recyclerComments.childCount;
        val firstVisibleItem = _llmReplies.findFirstVisibleItemPosition();
        val visibleThreshold = 15;
        if (!_loading && firstVisibleItem + visibleItemCount + visibleThreshold >= _comments.size) {
            loadNextPage();
        }
    }

    private fun loadNextPage() {
        val pager: IPager<IPlatformComment> = _commentsPager ?: return;

        if(pager.hasMorePages()) {
            setLoading(true);
            _nextPageHandler.run(pager);
        }
    }

    private fun onNextPageLoaded(comments: List<IPlatformComment>) {
        setLoading(false);

        if (comments.isEmpty()) {
            return;
        }

        val posBefore = _comments.size;
        _comments.addAll(comments);
        _adapterComments.notifyItemRangeInserted(_adapterComments.childToParentPosition(posBefore), comments.size);
    }

    private fun onCommentsLoaded(pager: IPager<IPlatformComment>) {
        setLoading(false);

        _comments.addAll(pager.getResults());
        _adapterComments.notifyDataSetChanged();
        _commentsPager = pager;
        onCommentsLoaded.emit(_comments.size);
    }

    fun load(readonly: Boolean, loader: suspend () -> IPager<IPlatformComment>) {
        cancel();

        _readonly = readonly;
        setLoading(true);
        _comments.clear();
        _commentsPager = null;
        _adapterComments.notifyDataSetChanged();

        _loader = loader;
        fetchComments();
    }

    private fun setLoading(loading: Boolean) {
        if (_loading == loading) {
            return;
        }

        _loading = loading;
        _adapterComments.setLoading(loading);
    }

    private fun fetchComments() {
        val loader = _loader ?: return;
        _taskLoadComments.run(loader);
    }

    fun clear() {
        cancel();
        _comments.clear();
        _commentsPager = null;
        _adapterComments.notifyDataSetChanged();
    }

    fun cancel() {
        _taskLoadComments.cancel();
        _nextPageHandler.cancel();
    }

    fun replaceComment(c: PolycentricPlatformComment, newComment: PolycentricPlatformComment) {
        val index = _comments.indexOf(c);
        _comments[index] = newComment;
        _adapterComments.notifyItemChanged(_adapterComments.childToParentPosition(index));
    }

    companion object {
        private const val TAG = "CommentsList";
    }
}