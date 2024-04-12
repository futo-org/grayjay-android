package com.futo.platformplayer.views.adapters.feedtypes

import android.view.ViewGroup
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.video.PlayerManager
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.adapters.ContentPreviewViewHolder


class PreviewVideoViewHolder : ContentPreviewViewHolder {

    val onVideoClicked = Event2<IPlatformVideo, Long>();
    val onChannelClicked = Event1<PlatformAuthorLink>();
    val onAddToClicked = Event1<IPlatformVideo>();
    val onAddToQueueClicked = Event1<IPlatformVideo>();
    val onAddToWatchLaterClicked = Event1<IPlatformVideo>();
    val onLongPress = Event1<IPlatformVideo>();

    //val context: Context;
    val currentVideo: IPlatformVideo? get() = view.currentVideo;

    override val content: IPlatformContent? get() = currentVideo;

    private val view: PreviewVideoView get() = itemView as PreviewVideoView;

    constructor(viewGroup: ViewGroup, feedStyle : FeedStyle, exoPlayer: PlayerManager? = null, shouldShowTimeBar: Boolean = true): super(
        PreviewVideoView(viewGroup.context, feedStyle, exoPlayer, shouldShowTimeBar)
    ) {
        view.onVideoClicked.subscribe(onVideoClicked::emit);
        view.onChannelClicked.subscribe(onChannelClicked::emit);
        view.onAddToClicked.subscribe(onAddToClicked::emit);
        view.onAddToQueueClicked.subscribe(onAddToQueueClicked::emit);
        view.onAddToWatchLaterClicked.subscribe(onAddToWatchLaterClicked::emit);
        view.onLongPress.subscribe(onLongPress::emit);
    }


    override fun bind(content: IPlatformContent) = view.bind(content);

    override fun preview(details: IPlatformContentDetails?, paused: Boolean) =  view.preview(details, paused);
    override fun stopPreview() = view.stopPreview();
    override fun pausePreview() = view.pausePreview();
    override fun resumePreview() = view.resumePreview();

    companion object {
        private val TAG = "VideoPreviewViewHolder"
    }
}
