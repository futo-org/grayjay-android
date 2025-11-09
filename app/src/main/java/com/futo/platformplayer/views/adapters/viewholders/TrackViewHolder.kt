package com.futo.platformplayer.views.adapters.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.toHumanDuration
import com.futo.platformplayer.views.adapters.AnyAdapter


class TrackViewHolder(private val _viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<IPlatformContent>(
    LayoutInflater.from(_viewGroup.context).inflate(
        R.layout.list_track,
        _viewGroup, false)) {

    val onClick = Event1<IPlatformContent>();
    val onOptions = Event1<IPlatformContent>();

    protected var _content: IPlatformContent? = null;
    //protected val _imageThumbnail: ImageView
    protected val _textName: TextView
    protected val _textMetadata: TextView

    protected val _imageSettings: ImageView;

    init {
        //_imageThumbnail = _view.findViewById(R.id.image_thumbnail);
        _textName = _view.findViewById(R.id.text_name);
        _textMetadata = _view.findViewById(R.id.text_metadata);
        _imageSettings = _view.findViewById(R.id.button_options);

        _view.setOnClickListener { _content?.let { onClick.emit(it) } };
        _imageSettings.setOnClickListener { _content?.let { onOptions.emit(it) } };
    }

    override fun bind(content: IPlatformContent) {
        _content = content;
        /*
        _imageThumbnail?.let {
            if (artist.thumbnail != null)
                Glide.with(it)
                    .load(artist.thumbnail)
                    .placeholder(R.drawable.placeholder_channel_thumbnail)
                    .into(it)
            else
                Glide.with(it).load(R.drawable.placeholder_channel_thumbnail).into(it);
        };
        */

        _textName.text = content.name;

        val metaComps = listOf(
            if(content is IPlatformVideo) "${content.duration.toHumanDuration(false)}" else null
        ).filterNotNull();

        _textMetadata.text = metaComps.joinToString(", ");
    }

}