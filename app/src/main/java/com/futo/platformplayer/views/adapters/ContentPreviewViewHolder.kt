package com.futo.platformplayer.views.adapters

import android.view.View
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails

abstract class ContentPreviewViewHolder(itemView: View) : ViewHolder(itemView) {
    abstract val content: IPlatformContent?;

    abstract fun bind(content: IPlatformContent);

    abstract fun preview(details: IPlatformContentDetails?, paused: Boolean);
    abstract fun stopPreview();
    abstract fun pausePreview();
    abstract fun resumePreview();

}