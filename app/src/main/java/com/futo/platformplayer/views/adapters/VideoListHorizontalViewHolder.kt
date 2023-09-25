package com.futo.platformplayer.views.adapters

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.toHumanTime

class VideoListHorizontalViewHolder : ViewHolder {
    private val _root: ConstraintLayout;
    private val _imageThumbnail: ImageView;
    private val _textName: TextView;
    private val _textAuthor: TextView;
    private val _textVideoDuration: TextView;
    private val _containerDuration: LinearLayout;
    private val _containerLive: LinearLayout;

    var video: IPlatformVideo? = null
        private set;

    val onClick = Event0();

    constructor(view: View) : super(view) {
        _root = view.findViewById(R.id.root);
        _imageThumbnail = view.findViewById(R.id.image_video_thumbnail);
        _imageThumbnail?.clipToOutline = true;
        _textName = view.findViewById(R.id.text_video_name);
        _textAuthor = view.findViewById(R.id.text_author);
        _textVideoDuration = view.findViewById(R.id.thumbnail_duration);
        _containerDuration = view.findViewById(R.id.thumbnail_duration_container);
        _containerLive = view.findViewById(R.id.thumbnail_live_container);

        _root?.setOnClickListener { onClick.emit(); };
    }

    fun bind(v: IPlatformVideo) {
        Glide.with(_imageThumbnail)
            .load(v.thumbnails.getLQThumbnail())
            .placeholder(R.drawable.placeholder_video_thumbnail)
            .into(_imageThumbnail);
        _textName.text = v.name;
        _textAuthor.text = v.author.name;
        _textVideoDuration.text = v.duration.toHumanTime(false);

        if(v.isLive) {
            _containerDuration.visibility = View.GONE;
            _containerLive.visibility = View.VISIBLE;
        }
        else {
            _containerLive.visibility = View.GONE;
            _containerDuration.visibility = View.VISIBLE;
        }

        video = v;
    }
}