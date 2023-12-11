package com.futo.platformplayer.views.adapters.feedtypes

import android.view.ViewGroup
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.adapters.ContentPreviewViewHolder


class PreviewPostViewHolder : ContentPreviewViewHolder {

    val onContentClicked = Event1<IPlatformContent>();
    val onChannelClicked = Event1<PlatformAuthorLink>();

    override val content: IPlatformContent? get() = view.content;

    private val view: PreviewPostView get() = itemView as PreviewPostView;

    constructor(viewGroup: ViewGroup, feedStyle : FeedStyle): super(
        PreviewPostView(viewGroup.context, feedStyle)
    ) {
        view.onContentClicked.subscribe(onContentClicked::emit);
        view.onChannelClicked.subscribe(onChannelClicked::emit);
    }


    override fun bind(content: IPlatformContent) = view.bind(content);

    override fun preview(details: IPlatformContentDetails?, paused: Boolean) {};
    override fun stopPreview() {};
    override fun pausePreview() {};
    override fun resumePreview() {};

    companion object {
        private val TAG = "VideoPreviewViewHolder"
    }
}
