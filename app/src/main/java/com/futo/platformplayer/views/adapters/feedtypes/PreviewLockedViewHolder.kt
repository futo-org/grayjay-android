package com.futo.platformplayer.views.adapters.feedtypes

import android.content.Context
import android.view.ViewGroup
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.adapters.ContentPreviewViewHolder


class PreviewLockedViewHolder : ContentPreviewViewHolder {
    override var content: IPlatformContent? = null;

    private val view: PreviewLockedView get() = itemView as PreviewLockedView;

    val onLockedUrlClicked = Event1<String>();

    val context: Context;

    constructor(viewGroup: ViewGroup, feedStyle: FeedStyle) : super(PreviewLockedView(viewGroup.context, feedStyle)) {
        context = itemView.context;
        view.onLockedUrlClicked.subscribe(onLockedUrlClicked::emit);
    }

    override fun bind(content: IPlatformContent) = view.bind(content);

    override suspend fun preview(details: IPlatformContentDetails?, paused: Boolean) { }
    override fun stopPreview() { }
    override fun pausePreview() { }
    override fun resumePreview() { }

    companion object {
        private val TAG = "PlaceholderPreviewViewHolder"
    }
}
