package com.futo.platformplayer.views.adapters

import android.view.View
import android.view.ViewGroup
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails

class EmptyPreviewViewHolder(viewGroup: ViewGroup) : ContentPreviewViewHolder(View(viewGroup.context)) {
    override val content: IPlatformContent?
        get() = null;

    override fun bind(content: IPlatformContent) {}

    override fun preview(details: IPlatformContentDetails?, paused: Boolean) {}

    override fun stopPreview() {}

    override fun pausePreview() {}

    override fun resumePreview() {}

}