package com.futo.platformplayer.views.adapters.feedtypes

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.nested.IPlatformNestedContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.debug.Stopwatch
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.video.PlayerManager
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.adapters.ContentPreviewViewHolder
import com.futo.platformplayer.views.adapters.EmptyPreviewViewHolder
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import okhttp3.internal.platform.Platform

class PreviewContentListAdapter : InsertedViewAdapterWithLoader<ContentPreviewViewHolder> {
    private var _initialPlay = true;
    private var _previewingViewHolder: ContentPreviewViewHolder? = null;
    private val _dataSet: ArrayList<IPlatformContent>;
    private val _exoPlayer: PlayerManager?;
    private val _feedStyle : FeedStyle;
    private var _paused: Boolean = false;
    private val _shouldShowTimeBar: Boolean

    val onUrlClicked = Event1<String>();
    val onContentUrlClicked = Event2<String, ContentType>();
    val onContentClicked = Event2<IPlatformContent, Long>();
    val onChannelClicked = Event1<PlatformAuthorLink>();
    val onAddToClicked = Event1<IPlatformContent>();
    val onAddToQueueClicked = Event1<IPlatformContent>();
    val onAddToWatchLaterClicked = Event1<IPlatformContent>();
    val onLongPress = Event1<IPlatformContent>();

    private var _taskLoadContent = TaskHandler<Pair<ContentPreviewViewHolder, IPlatformContent>, Pair<ContentPreviewViewHolder, IPlatformContentDetails>>(
        StateApp.instance.scopeGetter, { (viewHolder, video) ->
        val stopwatch = Stopwatch()
        val contentDetails = StatePlatform.instance.getContentDetails(video.url).await();
        stopwatch.logAndNext(TAG, "Retrieving video detail (IO thread)")
        return@TaskHandler Pair(viewHolder, contentDetails)
    }).exception<Throwable> { Logger.e(TAG, "Failed to retrieve preview content.", it) }.success { previewContentDetails(it.first, it.second) }

    constructor(context: Context, feedStyle : FeedStyle, dataSet: ArrayList<IPlatformContent>, exoPlayer: PlayerManager? = null,
                initialPlay: Boolean = false, viewsToPrepend: ArrayList<View> = arrayListOf(),
                viewsToAppend: ArrayList<View> = arrayListOf(), shouldShowTimeBar: Boolean = true) : super(context, viewsToPrepend, viewsToAppend) {

        this._feedStyle = feedStyle;
        this._dataSet = dataSet;
        this._initialPlay = initialPlay;
        this._exoPlayer = exoPlayer;
        this._shouldShowTimeBar = shouldShowTimeBar
    }

    override fun getChildCount(): Int = _dataSet.size;
    override fun getItemViewType(position: Int): Int {
        val p = parentToChildPosition(position);
        if (p < 0) {
            return -1;
        }

        val item = _dataSet.getOrNull(p) ?: return -1;
        return item.contentType.value;
    }
    override fun createChild(viewGroup: ViewGroup, viewType: Int): ContentPreviewViewHolder {
        if(viewType == -1)
            return EmptyPreviewViewHolder(viewGroup);
        val contentType = ContentType.fromInt(viewType);
        return when(contentType) {
            ContentType.PLACEHOLDER -> createPlaceholderViewHolder(viewGroup);
            ContentType.MEDIA -> createVideoPreviewViewHolder(viewGroup);
            ContentType.ARTICLE -> createPostViewHolder(viewGroup);
            ContentType.WEB -> createPostViewHolder(viewGroup);
            ContentType.POST -> createPostViewHolder(viewGroup);
            ContentType.PLAYLIST -> createPlaylistViewHolder(viewGroup);
            ContentType.NESTED_VIDEO -> createNestedViewHolder(viewGroup);
            ContentType.LOCKED -> createLockedViewHolder(viewGroup);
            ContentType.CHANNEL -> createChannelViewHolder(viewGroup)
            else -> EmptyPreviewViewHolder(viewGroup)
        }
    }

    private fun createPostViewHolder(viewGroup: ViewGroup): PreviewPostViewHolder = PreviewPostViewHolder(viewGroup, _feedStyle).apply {
        this.onContentClicked.subscribe { this@PreviewContentListAdapter.onContentClicked.emit(it, 0); }
        this.onChannelClicked.subscribe { this@PreviewContentListAdapter.onChannelClicked.emit(it); }
    }
    private fun createNestedViewHolder(viewGroup: ViewGroup): PreviewNestedVideoViewHolder = PreviewNestedVideoViewHolder(viewGroup, _feedStyle, _exoPlayer).apply {
        this.onContentUrlClicked.subscribe(this@PreviewContentListAdapter.onContentUrlClicked::emit);
        this.onVideoClicked.subscribe(this@PreviewContentListAdapter.onContentClicked::emit);
        this.onChannelClicked.subscribe(this@PreviewContentListAdapter.onChannelClicked::emit);
        this.onAddToClicked.subscribe(this@PreviewContentListAdapter.onAddToClicked::emit);
        this.onAddToQueueClicked.subscribe(this@PreviewContentListAdapter.onAddToQueueClicked::emit);
        this.onAddToWatchLaterClicked.subscribe(this@PreviewContentListAdapter.onAddToWatchLaterClicked::emit);
    };
    private fun createLockedViewHolder(viewGroup: ViewGroup): PreviewLockedViewHolder = PreviewLockedViewHolder(viewGroup, _feedStyle).apply {
        this.onLockedUrlClicked.subscribe(this@PreviewContentListAdapter.onUrlClicked::emit);
    };
    private fun createPlaceholderViewHolder(viewGroup: ViewGroup): PreviewPlaceholderViewHolder
        = PreviewPlaceholderViewHolder(viewGroup, _feedStyle);
    private fun createVideoPreviewViewHolder(viewGroup: ViewGroup): PreviewVideoViewHolder = PreviewVideoViewHolder(viewGroup, _feedStyle, _exoPlayer, _shouldShowTimeBar).apply {
            this.onVideoClicked.subscribe(this@PreviewContentListAdapter.onContentClicked::emit);
            this.onChannelClicked.subscribe(this@PreviewContentListAdapter.onChannelClicked::emit);
            this.onAddToClicked.subscribe(this@PreviewContentListAdapter.onAddToClicked::emit);
            this.onAddToQueueClicked.subscribe(this@PreviewContentListAdapter.onAddToQueueClicked::emit);
            this.onAddToWatchLaterClicked.subscribe(this@PreviewContentListAdapter.onAddToWatchLaterClicked::emit);
            this.onLongPress.subscribe(this@PreviewContentListAdapter.onLongPress::emit);
        };
    private fun createPlaylistViewHolder(viewGroup: ViewGroup): PreviewPlaylistViewHolder = PreviewPlaylistViewHolder(viewGroup, _feedStyle).apply {
        this.onPlaylistClicked.subscribe { this@PreviewContentListAdapter.onContentClicked.emit(it, 0L) };
        this.onChannelClicked.subscribe(this@PreviewContentListAdapter.onChannelClicked::emit);
    };
    private fun createChannelViewHolder(viewGroup: ViewGroup): PreviewChannelViewHolder = PreviewChannelViewHolder(viewGroup, _feedStyle, false).apply {
        //TODO: Maybe PlatformAuthorLink as is needs to be phased out?
        this.onClick.subscribe { this@PreviewContentListAdapter.onChannelClicked.emit(PlatformAuthorLink(it.id, it.name, it.url, it.thumbnail, it.subscribers)) };
    };

    override fun bindChild(holder: ContentPreviewViewHolder, pos: Int) {
        val value = _dataSet[pos];

        holder.bind(value);
        if (_initialPlay && pos == 0) {
            _initialPlay = false;

            if (_feedStyle != FeedStyle.THUMBNAIL) {
                preview(holder);
            }
        }
    }

    fun preview(viewHolder: ContentPreviewViewHolder) {
        Log.v(TAG, "previewing content");
        if (viewHolder == _previewingViewHolder)
            return

        val content = viewHolder.content ?: return
        if(content is IPlatformVideoDetails)
            previewContentDetails(viewHolder, content);
        else if(content is IPlatformVideo)
            _taskLoadContent.run(Pair(viewHolder, content));
        else if(content is IPlatformNestedContent)
            previewContentDetails(viewHolder, null);
    }
    fun stopPreview() {
        _taskLoadContent.cancel();
        _previewingViewHolder?.stopPreview();
        _previewingViewHolder = null;
    }
    fun pausePreview() {
        _previewingViewHolder?.pausePreview()
        _paused = true;
    }
    fun resumePreview() {
        _previewingViewHolder?.resumePreview()
        _paused = false;
    }

    fun release() {
        _taskLoadContent.dispose();
        onContentUrlClicked.clear();
        onUrlClicked.clear();
        onContentClicked.clear();
        onChannelClicked.clear();
        onAddToClicked.clear();
        onAddToQueueClicked.clear();
        onAddToWatchLaterClicked.clear();
    }

    private fun previewContentDetails(viewHolder: ContentPreviewViewHolder, videoDetails: IPlatformContentDetails?) {
        _previewingViewHolder?.stopPreview();
        viewHolder.preview(videoDetails, _paused);
        _previewingViewHolder = viewHolder;
    }

    companion object {
        private val TAG = "VideoPreviewListAdapter";
    }
}