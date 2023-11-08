package com.futo.platformplayer.views.adapters

import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.models.Playlist

class PlaylistsViewHolder : ViewHolder {
    private val _root: ConstraintLayout;
    private val _imageThumbnail: ImageView;
    private val _textName: TextView;
    private val _textMetadata: TextView;
    private val _buttonTrash: ImageButton;
    //private val _buttonPlay: ImageButton;

    var playlist: Playlist? = null
        private set;

    val onClick = Event0();
    val onRemove = Event0();
    val onPlay = Event0();

    constructor(view: View) : super(view) {
        _root = view.findViewById(R.id.root);
        _imageThumbnail = view.findViewById(R.id.image_video_thumbnail);
        _textName = view.findViewById(R.id.text_name);
        _textMetadata = view.findViewById(R.id.text_metadata);
        _buttonTrash = view.findViewById(R.id.button_trash);
        //_buttonPlay = view.findViewById(R.id.button_play);

        _root.setOnClickListener { onClick.emit(); };
        _buttonTrash.setOnClickListener { onRemove.emit(); };
        //_buttonPlay.setOnClickListener { onPlay.emit(); };
    }

    fun bind(p: Playlist) {
        if (p.videos.isNotEmpty()) {
            Glide.with(_imageThumbnail)
                .load(p.videos[0].thumbnails.getMinimumThumbnail(380))
                .placeholder(R.drawable.placeholder_video_thumbnail)
                .crossfade()
                .into(_imageThumbnail);
        } else {
            _imageThumbnail.setImageResource(R.drawable.placeholder_video_thumbnail);
        }

        _textName.text = p.name;
        _textMetadata.text = "${p.videos.size} videos";
        playlist = p;
    }
}