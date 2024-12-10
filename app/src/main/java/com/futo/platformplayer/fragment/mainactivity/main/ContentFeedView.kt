package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.models.post.IPlatformPost
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateMeta
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.video.PlayerManager
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.adapters.ContentPreviewViewHolder
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import com.futo.platformplayer.views.adapters.InsertedViewHolder
import com.futo.platformplayer.views.adapters.feedtypes.PreviewContentListAdapter
import com.futo.platformplayer.views.adapters.feedtypes.PreviewNestedVideoViewHolder
import com.futo.platformplayer.views.adapters.feedtypes.PreviewVideoViewHolder
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuItem
import com.futo.platformplayer.views.overlays.slideup.SlideUpMenuOverlay
import com.futo.platformplayer.withTimestamp
import kotlin.math.floor
import kotlin.math.max

abstract class ContentFeedView<TFragment> : FeedView<TFragment, IPlatformContent, IPlatformContent, IPager<IPlatformContent>, ContentPreviewViewHolder> where TFragment : MainFragment {
    private var _exoPlayer: PlayerManager? = null;

    override val feedStyle: FeedStyle = FeedStyle.PREVIEW;

    private var _previewsEnabled: Boolean = true;
    override val visibleThreshold: Int get() = if (feedStyle == FeedStyle.PREVIEW) { 5 } else { 10 };
    protected lateinit var headerView: LinearLayout;
    private var _videoOptionsOverlay: SlideUpMenuOverlay? = null;
    protected open val shouldShowTimeBar: Boolean get() = true

    constructor(fragment: TFragment, inflater: LayoutInflater, cachedRecyclerData: RecyclerData<InsertedViewAdapterWithLoader<ContentPreviewViewHolder>, GridLayoutManager, IPager<IPlatformContent>, IPlatformContent, IPlatformContent, InsertedViewHolder<ContentPreviewViewHolder>>? = null) : super(fragment, inflater, cachedRecyclerData)

    override fun filterResults(results: List<IPlatformContent>): List<IPlatformContent> {
        return results;
    }

    override fun createAdapter(recyclerResults: RecyclerView, context: Context, dataset: ArrayList<IPlatformContent>): InsertedViewAdapterWithLoader<ContentPreviewViewHolder> {
        val player = StatePlayer.instance.getThumbnailPlayerOrCreate(context);
        player.modifyState("ThumbnailPlayer") { state -> state.muted = true };
        _exoPlayer = player;

        return PreviewContentListAdapter(context, feedStyle, dataset, player, _previewsEnabled, arrayListOf(), arrayListOf(), shouldShowTimeBar).apply {
            attachAdapterEvents(this);
        }
    }

    private fun attachAdapterEvents(adapter: PreviewContentListAdapter) {
        adapter.onContentUrlClicked.subscribe(this, this@ContentFeedView::onContentUrlClicked);
        adapter.onUrlClicked.subscribe(this, this@ContentFeedView::onUrlClicked);
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
            }
        };
        adapter.onAddToWatchLaterClicked.subscribe(this) {
            if(it is IPlatformVideo) {
                StatePlaylists.instance.addToWatchLater(SerializedPlatformVideo.fromVideo(it), true);
                UIDialogs.toast("Added to watch later\n[${it.name}]");
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
            _videoOptionsOverlay = UISlideOverlays.showVideoOptionsOverlay(content, it, SlideUpMenuItem(
                context,
                R.drawable.ic_visibility_off,
                context.getString(R.string.hide),
                context.getString(R.string.hide_from_home),
                tag = "hide",
                call = { StateMeta.instance.addHiddenVideo(content.url);
                    if (fragment is HomeFragment) {
                        val removeIndex = recyclerData.results.indexOf(content);
                        if (removeIndex >= 0) {
                            recyclerData.results.removeAt(removeIndex);
                            recyclerData.adapter.notifyItemRemoved(recyclerData.adapter.childToParentPosition(removeIndex));
                        }
                    }
                }),
                SlideUpMenuItem(context,
                    R.drawable.ic_playlist,
                    context.getString(R.string.play_feed_as_queue),
                    context.getString(R.string.play_entire_feed),
                    tag = "playFeed",
                    call = {
                        val newQueue = listOf(content) + recyclerData.results
                            .filterIsInstance<IPlatformVideo>()
                            .filter { it != content };
                        StatePlayer.instance.setQueue(newQueue, StatePlayer.TYPE_QUEUE, "Feed Queue",
                            focus = true,
                            shuffle = false
                        );
                    })
            );
        }
    }

    private fun detachAdapterEvents() {
        val adapter = recyclerData.adapter as PreviewContentListAdapter? ?: return;
        adapter.onContentUrlClicked.remove(this);
        adapter.onUrlClicked.remove(this);
        adapter.onContentClicked.remove(this);
        adapter.onChannelClicked.remove(this);
        adapter.onAddToClicked.remove(this);
        adapter.onAddToQueueClicked.remove(this);
        adapter.onAddToWatchLaterClicked.remove(this);
        adapter.onLongPress.remove(this);
    }

    override fun onRestoreCachedData(cachedData: RecyclerData<InsertedViewAdapterWithLoader<ContentPreviewViewHolder>, GridLayoutManager, IPager<IPlatformContent>, IPlatformContent, IPlatformContent, InsertedViewHolder<ContentPreviewViewHolder>>) {
        super.onRestoreCachedData(cachedData)

        (cachedData.adapter as PreviewContentListAdapter?)?.let { attachAdapterEvents(it) };
    }

    override fun createLayoutManager(
        recyclerResults: RecyclerView,
        context: Context
    ): GridLayoutManager {
        val glmResults =
            GridLayoutManager(
                context,
                max((resources.configuration.screenWidthDp.toDouble() / resources.getInteger(R.integer.column_width_dp)).toInt(), 1)
            );
        return glmResults
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
                StatePlayer.instance.insertToQueue(content, true);
            } else {
                if (Settings.instance.playback.shouldResumePreview(time))
                    fragment.navigate<VideoDetailFragment>(content.withTimestamp(time)).maximizeVideoDetail();
                else
                    fragment.navigate<VideoDetailFragment>(content).maximizeVideoDetail();
            }
        } else if (content is IPlatformPlaylist) {
            fragment.navigate<RemotePlaylistFragment>(content);
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
            ContentType.PLAYLIST -> fragment.navigate<RemotePlaylistFragment>(url);
            ContentType.URL -> fragment.navigate<BrowserFragment>(url);
            else -> {};
        }
    }
    protected open fun onUrlClicked(url: String) {
        fragment.navigate<BrowserFragment>(url);
    }

    private fun playPreview() {
        if(feedStyle == FeedStyle.THUMBNAIL || recyclerData.layoutManager.spanCount > 1)
            return;

        val firstVisible = recyclerData.layoutManager.findFirstVisibleItemPosition()
        val lastVisible = recyclerData.layoutManager.findLastVisibleItemPosition()
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

    private fun stopVideo() {
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
        private const val TAG = "ContentFeedView";
    }
}