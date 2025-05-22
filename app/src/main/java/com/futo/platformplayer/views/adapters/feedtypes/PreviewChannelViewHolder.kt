package com.futo.platformplayer.views.adapters.feedtypes

import android.view.ViewGroup
import com.futo.platformplayer.api.media.models.IPlatformChannelContent
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.fragment.mainactivity.main.CreatorFeedView
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.adapters.ChannelView
import com.futo.platformplayer.views.adapters.ContentPreviewViewHolder
import com.futo.platformplayer.views.adapters.PlaylistView


class PreviewChannelViewHolder : ContentPreviewViewHolder {
    val onClick = Event1<IPlatformChannelContent>();

    val currentChannel: IPlatformChannelContent? get() = view.currentChannel;

    override val content: IPlatformContent? get() = currentChannel;

    private val view: ChannelView get() = itemView as ChannelView;

    constructor(viewGroup: ViewGroup, feedStyle: FeedStyle, tiny: Boolean): super(ChannelView(viewGroup.context, feedStyle, tiny)) {
        view.onClick.subscribe(onClick::emit);
    }

    override fun bind(content: IPlatformContent) = view.bind(content);

    override fun preview(details: IPlatformContentDetails?, paused: Boolean) = Unit;
    override fun stopPreview() = Unit;
    override fun pausePreview() = Unit;
    override fun resumePreview() = Unit;

    companion object {
        private val TAG = "PreviewChannelViewHolder"
    }
}