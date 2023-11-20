package com.futo.platformplayer.views.adapters.feedtypes

import android.content.Context
import android.graphics.drawable.Animatable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.contents.PlatformContentPlaceholder
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.adapters.ContentPreviewViewHolder
import com.futo.platformplayer.views.platform.PlatformIndicator


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

    override fun preview(video: IPlatformContentDetails?, paused: Boolean) { }
    override fun stopPreview() { }
    override fun pausePreview() { }
    override fun resumePreview() { }

    companion object {
        private val TAG = "PlaceholderPreviewViewHolder"
    }
}
