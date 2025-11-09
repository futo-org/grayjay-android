package com.futo.platformplayer.views

import android.content.Context
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1

class AlbumHeaderView: ConstraintLayout {


    val textName: TextView;
    val textMetadata: TextView;

    val imageThumbnail: ImageView;
    val imageThumbnailBackground: ImageView;

    val buttonPlayAll: LinearLayout;
    val buttonShuffle: LinearLayout;

    val onPlayAll = Event0();
    val onShuffle = Event0();

    constructor(context: Context) : super(context) {
        inflate(context, R.layout.view_album_header, this)

        textName = findViewById(R.id.text_name);
        textMetadata = findViewById(R.id.text_metadata);
        imageThumbnail = findViewById(R.id.image_thumbnail);
        imageThumbnailBackground = findViewById(R.id.image_thumbnail_background);
        buttonPlayAll = findViewById(R.id.button_play_all);
        buttonShuffle = findViewById(R.id.button_shuffle);

        buttonPlayAll.setOnClickListener { onPlayAll.emit() };
        buttonShuffle.setOnClickListener { onShuffle.emit() };
    }

    fun setThumbnail(thumbnail: String?) {
        if (thumbnail != null)
            Glide.with(imageThumbnail)
                .load(thumbnail)
                .placeholder(R.drawable.placeholder_channel_thumbnail)
                .into(imageThumbnail)
        else
            Glide.with(imageThumbnail)
                .load(R.drawable.placeholder_channel_thumbnail)
                .into(imageThumbnail);
        if (thumbnail != null)
            Glide.with(imageThumbnailBackground)
                .load(thumbnail)
                .placeholder(R.drawable.placeholder_channel_thumbnail)
                .into(imageThumbnailBackground)
        else
            Glide.with(imageThumbnailBackground)
                .load(R.drawable.placeholder_channel_thumbnail)
                .into(imageThumbnailBackground);

    }

    fun setName(str: String){
        textName.text = str;
    }
    fun setMetadata(str: String) {
        textMetadata.text = str;
    }
}