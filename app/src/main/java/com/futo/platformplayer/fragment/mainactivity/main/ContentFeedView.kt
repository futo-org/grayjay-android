package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.models.post.IPlatformPost
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.structures.*
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateMeta
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.video.PlayerManager
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.adapters.PreviewContentListAdapter
import com.futo.platformplayer.views.adapters.ContentPreviewViewHolder
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import com.futo.platformplayer.views.adapters.InsertedViewHolder
import com.futo.platformplayer.views.adapters.PreviewNestedVideoViewHolder
import com.futo.platformplayer.views.adapters.PreviewVideoViewHolder
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuItem
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuOverlay
import kotlin.math.floor

abstract class ContentFeedView<TFragment> : FeedView<TFragment, IPlatformContent, IPlatformContent, IPager<IPlatformContent>, ContentPreviewViewHolder> where TFragment : MainFragment {
    private var _exoPlayer: PlayerManager? = null;

    override val feedStyle: FeedStyle = FeedStyle.PREVIEW;

    private var _previewsEnabled: Boolean = true;
    override val visibleThreshold: Int get() = if (feedStyle == FeedStyle.PREVIEW) { 5 } else { 10 };
    protected lateinit var headerView: LinearLayout;
    private var _videoOptionsOverlay: SlideUpMenuOverlay? = null;

    constructor(fragment: TFragment, inflater: LayoutInflater, cachedRecyclerData: RecyclerData<InsertedViewAdapterWithLoader<ContentPreviewViewHolder>, LinearLayoutManager, IPager<IPlatformContent>, IPlatformContent, IPlatformContent, InsertedViewHolder<ContentPreviewViewHolder>>? = null) : super(fragment, inflater, cachedRecyclerData) {

    }

    override fun filterResults(results: List<IPlatformContent>): List<IPlatformContent> {
        return results;
    }

    override fun createAdapter(recyclerResults: RecyclerView, context: Context, dataset: ArrayList<IPlatformContent>): InsertedViewAdapterWithLoader<ContentPreviewViewHolder> {
        val player = StatePlayer.instance.getThumbnailPlayerOrCreate(context);
        player.modifyState("ThumbnailPlayer", { state -> state.muted = true });
        _exoPlayer = player;

        val v = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            orientation = LinearLayout.VERTICAL;
        };
        headerView = v;

        return PreviewContentListAdapter(context, feedStyle, dataset, player, _previewsEnabled, arrayListOf(v)).apply {
            attachAdapterEvents(this);
        }
    }

    private fun attachAdapterEvents(adapter: PreviewContentListAdapter) {
        adapter.onContentUrlClicked.subscribe(this, this@ContentFeedView::onContentUrlClicked);
        adapter.onContentClicked.subscribe(this) { content, time ->
            this@ContentFeedView.onContentClicked(content, time);
        };
        adapter.onChannelClicked.subscribe(this) { fragment.navigate<ChannelFragment>(it) };
        adapter.onAddToClicked.subscribe(this) { content ->
            //TODO: Reconstruct search video from detail if search is null
            if(content is IPlatformVideo) {
                showVideoOptionsOverlay(content)
            }
        };
        adapter.onAddToQueueClicked.subscribe(this) {
            if(it is IPlatformVideo) {
                StatePlayer.instance.addToQueue(it);
                val name = if (it.name.length > 20) (it.name.subSequence(0, 20).toString() + "...") else it.name;
                UIDialogs.toast(context, context.getString(R.string.queued) + " [$name]", false);
            }
        };
        adapter.onLongPress.subscribe(this) {
            if (it is IPlatformVideo) {
                showVideoOptionsOverlay(it)
            }
        };
    }

    fun onBackPressed(): Boolean {
        val videoOptionsOverlay = _videoOptionsOverlay
        if (videoOptionsOverlay != null) {
            if (videoOptionsOverlay.isVisible) {
                videoOptionsOverlay.hide();
                _videoOptionsOverlay = null
                return true;
            }

            _videoOptionsOverlay = null
            return false
        }

        return false
    }

    private fun showVideoOptionsOverlay(content: IPlatformVideo) {
        _overlayContainer.let {
            _videoOptionsOverlay = UISlideOverlays.showVideoOptionsOverlay(content, it, SlideUpMenuItem(context, R.drawable.ic_visibility_off, context.getString(R.string.hide), context.getString(R.string.hide_from_home), "hide",
                { StateMeta.instance.addHiddenVideo(content.url);
                    if (fragment is HomeFragment) {
                        val removeIndex = recyclerData.results.indexOf(content);
                        if (removeIndex >= 0) {
                            recyclerData.results.removeAt(removeIndex);
                            recyclerData.adapter.notifyItemRemoved(recyclerData.adapter.childToParentPosition(removeIndex));
                        }
                    }
                }),
                SlideUpMenuItem(context, R.drawable.ic_playlist, context.getString(R.string.play_feed_as_queue), context.getString(R.string.play_entire_feed), "playFeed",
                    {
                        val newQueue = listOf(content) + recyclerData.results
                            .filterIsInstance<IPlatformVideo>()
                            .filter { it != content };
                        StatePlayer.instance.setQueue(newQueue, StatePlayer.TYPE_QUEUE, "Feed Queue", true, false);
                    })
            );
        }
    }

    private fun detachAdapterEvents() {
        val adapter = recyclerData.adapter as PreviewContentListAdapter? ?: return;
        adapter.onContentUrlClicked.remove(this);
        adapter.onContentClicked.remove(this);
        adapter.onChannelClicked.remove(this);
        adapter.onAddToClicked.remove(this);
        adapter.onAddToQueueClicked.remove(this);
        adapter.onLongPress.remove(this);
    }

    override fun onRestoreCachedData(cachedData: RecyclerData<InsertedViewAdapterWithLoader<ContentPreviewViewHolder>, LinearLayoutManager, IPager<IPlatformContent>, IPlatformContent, IPlatformContent, InsertedViewHolder<ContentPreviewViewHolder>>) {
        super.onRestoreCachedData(cachedData)
        val v = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            orientation = LinearLayout.VERTICAL;
        };
        headerView = v;
        cachedData.adapter.viewsToPrepend.add(v);
        (cachedData.adapter as PreviewContentListAdapter?)?.let { attachAdapterEvents(it) };
    }

    override fun createLayoutManager(recyclerResults: RecyclerView, context: Context): LinearLayoutManager {
        val llmResults = LinearLayoutManager(context);
        llmResults.orientation = LinearLayoutManager.VERTICAL;
        return llmResults;
    }

    override fun onScrollStateChanged(newState: Int) {
        if (!_previewsEnabled)
            return;

        if (newState == RecyclerView.SCROLL_STATE_IDLE)
            playPreview();
    }

    protected open fun onContentClicked(content: IPlatformContent, time: Long) {
        if(content is IPlatformVideo) {
            if (StatePlayer.instance.hasQueue) {
                StatePlayer.instance.addToQueue(content)
            } else {
                if (Settings.instance.playback.shouldResumePreview(time))
                    fragment.navigate<VideoDetailFragment>(content.withTimestamp(time)).maximizeVideoDetail();
                else
                    fragment.navigate<VideoDetailFragment>(content).maximizeVideoDetail();
            }
        } else if (content is IPlatformPlaylist) {
            fragment.navigate<PlaylistFragment>(content);
        } else if (content is IPlatformPost) {
            fragment.navigate<PostDetailFragment>(content);
        }
    }
    protected open fun onContentUrlClicked(url: String, contentType: ContentType) {
        when(contentType) {
            ContentType.MEDIA -> {
                StatePlayer.instance.clearQueue();
                fragment.navigate<VideoDetailFragment>(url).maximizeVideoDetail();
            };
            ContentType.PLAYLIST -> fragment.navigate<PlaylistFragment>(url);
            ContentType.URL -> fragment.navigate<BrowserFragment>(url);
            else -> {};
        }
    }

    private fun playPreview() {
        if(feedStyle == FeedStyle.THUMBNAIL)
            return;

        val firstVisible = recyclerData.layoutManager.findFirstVisibleItemPosition();
        val lastVisible = recyclerData.layoutManager.findLastVisibleItemPosition();
        val itemsVisible = lastVisible - firstVisible + 1;
        val autoPlayIndex = (firstVisible + floor(itemsVisible / 2.0 + 0.49).toInt()).coerceAtLeast(0).coerceAtMost((recyclerData.results.size - 1));

        Log.v(TAG, "auto play index=$autoPlayIndex");
        val viewHolder = _recyclerResults.findViewHolderForAdapterPosition(autoPlayIndex) ?: return;
        Logger.i(TAG, "viewHolder=$viewHolder")
        if (viewHolder !is InsertedViewHolder<*>) {
            return;
        }

        if (viewHolder.childViewHolder !is PreviewVideoViewHolder && viewHolder.childViewHolder !is PreviewNestedVideoViewHolder) {
            return;
        }

        //TODO: Is this still necessary?
        if(viewHolder.childViewHolder is ContentPreviewViewHolder)
            (recyclerData.adapter as PreviewContentListAdapter?)?.preview(viewHolder.childViewHolder)
    }

    fun stopVideo() {
        //TODO: Is this still necessary?
        (recyclerData.adapter as PreviewContentListAdapter?)?.stopPreview();
    }

    fun onPause() {
        stopVideo();
    }

    override fun cleanup() {
        super.cleanup();
        val viewCount = recyclerData.adapter.viewsToPrepend.size;
        detachAdapterEvents();
        recyclerData.adapter.viewsToPrepend.clear();
        recyclerData.adapter.notifyItemRangeRemoved(0, viewCount);
        (recyclerData.adapter as PreviewContentListAdapter?)?.release();
    }

    fun setPreviewsEnabled(previewsEnabled: Boolean) {
        if (!previewsEnabled)
            stopVideo();
        else
            playPreview();

        _previewsEnabled = previewsEnabled;
    }

    companion object {
        private val TAG = "ContentFeedView";
    }
}