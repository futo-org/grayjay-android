package com.futo.platformplayer.views.adapters.feedtypes

import android.view.ViewGroup
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.video.PlayerManager
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.adapters.ContentPreviewViewHolder


class PreviewNestedVideoViewHolder : ContentPreviewViewHolder {
    val onContentUrlClicked = Event2<String, ContentType>();
    val onVideoClicked = Event2<IPlatformVideo, Long>();
    val onChannelClicked = Event1<PlatformAuthorLink>();
    val onAddToClicked = Event1<IPlatformVideo>();
    val onAddToQueueClicked = Event1<IPlatformVideo>();

    override val content: IPlatformContent? get() = view.content;
    private val view: PreviewNestedVideoView get() = itemView as PreviewNestedVideoView;

    constructor(viewGroup: ViewGroup, feedStyle : FeedStyle, exoPlayer: PlayerManager? = null): super(
        PreviewNestedVideoView(viewGroup.context, feedStyle, exoPlayer)
    ) {
        view.onContentUrlClicked.subscribe(onContentUrlClicked::emit);
        view.onVideoClicked.subscribe(onVideoClicked::emit);
        view.onChannelClicked.subscribe(onChannelClicked::emit);
        view.onAddToClicked.subscribe(onAddToClicked::emit);
        view.onAddToQueueClicked.subscribe(onAddToQueueClicked::emit);
    }


    override fun bind(content: IPlatformContent) {
        view.bind(content);
    }

    override fun preview(details: IPlatformContentDetails?, paused: Boolean) {
        view.preview(details, paused);
    }

    override fun stopPreview() {
        view.stopPreview();
    }

    override fun pausePreview() {
        view.pausePreview();
    }

    override fun resumePreview() {
        view.resumePreview();
    }



    companion object {
        private val TAG = "PreviewNestedVideoViewHolder"
    }
}
