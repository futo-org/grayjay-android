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
import com.futo.platformplayer.states.Artist
import com.futo.platformplayer.views.adapters.AnyAdapter


class ArtistTileViewHolder(val _viewGroup: ViewGroup) : AnyAdapter.AnyViewHolder<Artist>(
    LayoutInflater.from(_viewGroup.context).inflate(
        R.layout.list_artist_tile,
        _viewGroup, false)) {

    val onClick = Event1<Artist?>();

    protected var _artist: Artist? = null;
    protected val _imageThumbnail: ImageView
    protected val _textName: TextView
    protected val _textMetadata: TextView

    init {
        _imageThumbnail = _view.findViewById(R.id.image_thumbnail);
        _textName = _view.findViewById(R.id.text_name);
        _textMetadata = _view.findViewById(R.id.text_metadata);

        _view.setOnClickListener { onClick.emit(_artist) };
    }


    override fun bind(artist: Artist) {
        _artist = artist;
        _imageThumbnail?.let {
            val thumbnail = artist.getThumbnailOrAlbum();
            if (thumbnail != null)
                Glide.with(it)
                    .load(thumbnail)
                    .placeholder(R.drawable.ic_artist)
                    .into(it)
            else
                Glide.with(it).load(R.drawable.ic_artist).into(it);
        };

        _textName.text = artist.name;
        _textMetadata.text = ""// "${artist.countTracks} tracks";
    }

}