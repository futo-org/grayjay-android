package com.futo.platformplayer.views.adapters.viewholders

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.states.Album
import com.futo.platformplayer.views.adapters.AnyAdapter


class AlbumTileViewHolder(val _viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<Album>(
    LayoutInflater.from(_viewGroup.context).inflate(
        R.layout.list_album_tile,
        _viewGroup, false)) {

    val onClick = Event1<Album?>();

    protected var _album: Album? = null;
    protected val _imageThumbnail: ImageView
    protected val _textName: TextView
    protected val _textMetadata: TextView

    init {
        _imageThumbnail = _view.findViewById(R.id.image_thumbnail);
        _textName = _view.findViewById(R.id.text_name);
        _textMetadata = _view.findViewById(R.id.text_metadata);

        _view.setOnClickListener { onClick.emit(_album) };
    }


    override fun bind(album: Album) {
        _album = album;
        _imageThumbnail?.let {
            if (album.thumbnail != null)
                Glide.with(it)
                    .load(album.thumbnail)
                    .placeholder(R.drawable.unknown_music)
                    .into(it)
            else
                Glide.with(it).load(R.drawable.unknown_music).into(it);
        };

        _textName.text = album.name;
        _textMetadata.text = album.artist ?: "";
    }

}