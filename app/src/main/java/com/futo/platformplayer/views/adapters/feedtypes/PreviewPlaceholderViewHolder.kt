package com.futo.platformplayer.views.adapters.feedtypes

import android.content.Context
import android.graphics.drawable.Animatable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.contents.PlatformContentPlaceholder
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.adapters.ContentPreviewViewHolder
import com.futo.platformplayer.views.platform.PlatformIndicator


class PreviewPlaceholderViewHolder : ContentPreviewViewHolder {
    override var content: IPlatformContent? = null;

    private val _loader: ImageView;
    private val _platformIndicator: PlatformIndicator;
    private val _error: TextView;

    val context: Context;

    //TODO: Aspect ratio sizing of layout
    constructor(viewGroup: ViewGroup, feedStyle: FeedStyle) : super(LayoutInflater.from(viewGroup.context).inflate(when(feedStyle) {
                FeedStyle.PREVIEW -> R.layout.list_placeholder_preview
                FeedStyle.THUMBNAIL -> R.layout.list_placeholder_thumbnail
                else -> R.layout.list_placeholder_thumbnail
            }, viewGroup, false)) {
        context = itemView.context;
        _loader = itemView.findViewById(R.id.loader);
        _platformIndicator = itemView.findViewById(R.id.thumbnail_platform);
        _error = itemView.findViewById(R.id.text_error);

        (_loader.drawable as Animatable?)?.start();
    }

    override fun bind(content: IPlatformContent) {
        if(content is PlatformContentPlaceholder) {
            _platformIndicator.setPlatformFromClientID(content.id.pluginId);
            _error.text = content.error?.message ?: "";
            if(content.error != null) {
                _loader.visibility = View.GONE;
                (_loader.drawable as Animatable?)?.stop();
            }
            else {
                _loader.visibility = View.VISIBLE;
                (_loader.drawable as Animatable?)?.start();
            }
        }
        else {
            _platformIndicator.clearPlatform();
            (_loader.drawable as Animatable?)?.stop();
        }
    }

    override fun preview(video: IPlatformContentDetails?, paused: Boolean) { }
    override fun stopPreview() { }
    override fun pausePreview() { }
    override fun resumePreview() { }

    companion object {
        private val TAG = "PlaceholderPreviewViewHolder"
    }
}
