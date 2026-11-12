package com.futo.platformplayer.views.adapters.viewholders

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.states.Album
import com.futo.platformplayer.states.Artist
import com.futo.platformplayer.toHumanNowDiffString
import com.futo.platformplayer.toHumanTime
import com.futo.platformplayer.views.adapters.AnyAdapter
import com.futo.platformplayer.withMaxSizePx
import com.google.android.material.imageview.ShapeableImageView


class LocalVideoTileViewHolder(val _viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<IPlatformVideo>(
    LayoutInflater.from(_viewGroup.context).inflate(
        R.layout.list_video_thumbnail_tile,
        _viewGroup, false)) {

    val onClick = Event1<IPlatformVideo?>();

    protected var _content: IPlatformVideo? = null;
    protected val _imageThumbnail: ShapeableImageView
    protected val _textDuration: TextView;
    protected val _textName: TextView
    protected val _textMetadata: TextView

    init {
        _imageThumbnail = _view.findViewById(R.id.image_video_thumbnail);
        _textDuration = _view.findViewById(R.id.thumbnail_duration);
        _textName = _view.findViewById(R.id.text_video_name);
        _textMetadata = _view.findViewById(R.id.text_video_metadata);

        _view.setOnClickListener { onClick.emit(_content) };
    }


    override fun bind(content: IPlatformVideo) {
        _content = content;
        _imageThumbnail?.let {
            if (content.thumbnails.getHQThumbnail() != null)
                Glide.with(it)
                    .load(content.thumbnails.getHQThumbnail())
                    .placeholder(R.drawable.unknown_music)
                    .withMaxSizePx()
                    .into(it)
            else
                Glide.with(it).load(R.drawable.unknown_music).into(it);
        };

        _textName.text = content.name;
        _textMetadata.text = content.datetime?.toHumanNowDiffString();
        _textDuration.text = content.duration.toHumanTime(false) + " ago";
        _textDuration.isVisible = content.duration > 0;
    }

}