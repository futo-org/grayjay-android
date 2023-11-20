package com.futo.platformplayer.views.adapters.feedtypes

import android.view.ViewGroup
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.adapters.ContentPreviewViewHolder
import com.futo.platformplayer.views.adapters.PlaylistView


class PreviewPlaylistViewHolder : ContentPreviewViewHolder {
    val onPlaylistClicked = Event1<IPlatformPlaylist>();
    val onChannelClicked = Event1<PlatformAuthorLink>();

    val currentPlaylist: IPlatformPlaylist? get() = view.currentPlaylist;

    override val content: IPlatformContent? get() = currentPlaylist;

    private val view: PlaylistView get() = itemView as PlaylistView;

    constructor(viewGroup: ViewGroup, feedStyle : FeedStyle): super(PlaylistView(viewGroup.context, feedStyle)) {
        view.onPlaylistClicked.subscribe(onPlaylistClicked::emit);
        view.onChannelClicked.subscribe(onChannelClicked::emit);
    }

    override fun bind(content: IPlatformContent) = view.bind(content);

    override fun preview(details: IPlatformContentDetails?, paused: Boolean) = Unit;
    override fun stopPreview() = Unit;
    override fun pausePreview() = Unit;
    override fun resumePreview() = Unit;

    companion object {
        private val TAG = "PlaylistViewHolder"
    }
}
