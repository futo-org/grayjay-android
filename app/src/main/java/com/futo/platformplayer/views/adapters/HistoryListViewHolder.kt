package com.futo.platformplayer.views.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.images.GlideHelper.Companion.crossfade
import com.futo.platformplayer.models.HistoryVideo
import com.futo.platformplayer.toHumanNumber
import com.futo.platformplayer.toHumanTime
import com.futo.platformplayer.views.others.ProgressBar

class HistoryListViewHolder : ViewHolder {
    private val _root: ConstraintLayout;
    private val _imageThumbnail: ImageView;
    private val _textName: TextView;
    private val _textAuthor: TextView;
    private val _textMetadata: TextView;
    private val _textVideoDuration: TextView;
    private val _containerDuration: LinearLayout;
    private val _containerLive: LinearLayout;
    private val _imageRemove: ImageButton;
    private val _textHeader: TextView;
    private val _timeBar: ProgressBar;

    var video: HistoryVideo? = null
        private set;

    val onClick = Event1<HistoryVideo>();
    val onRemove = Event1<HistoryVideo>();

    constructor(viewGroup: ViewGroup) : super(LayoutInflater.from(viewGroup.context).inflate(R.layout.list_history, viewGroup, false)) {
        _root = itemView.findViewById(R.id.root);
        _imageThumbnail = itemView.findViewById(R.id.image_video_thumbnail);
        _imageThumbnail.clipToOutline = true;
        _textName = itemView.findViewById(R.id.text_video_name);
        _textAuthor = itemView.findViewById(R.id.text_author);
        _textMetadata = itemView.findViewById(R.id.text_video_metadata);
        _textVideoDuration = itemView.findViewById(R.id.thumbnail_duration);
        _containerDuration = itemView.findViewById(R.id.thumbnail_duration_container);
        _containerLive = itemView.findViewById(R.id.thumbnail_live_container);
        _imageRemove = itemView.findViewById(R.id.image_trash);
        _textHeader = itemView.findViewById(R.id.text_header);
        _timeBar = itemView.findViewById(R.id.time_bar);

        _root.setOnClickListener {
            val v = video ?: return@setOnClickListener;
            onClick.emit(v);
        };

        _imageRemove.setOnClickListener {
            val v = video ?: return@setOnClickListener;
            onRemove.emit(v);
        };
    }

    fun bind(v: HistoryVideo, watchTime: String?) {
        Glide.with(_imageThumbnail)
            .load(v.video.thumbnails.getLQThumbnail())
            .placeholder(R.drawable.placeholder_video_thumbnail)
            .crossfade()
            .into(_imageThumbnail);

        _textName.text = v.video.name;
        _textAuthor.text = v.video.author.name;
        _textVideoDuration.text = v.video.duration.toHumanTime(false);

        if(v.video.isLive) {
            _containerDuration.visibility = View.GONE;
            _containerLive.visibility = View.VISIBLE;
        }
        else {
            _containerLive.visibility = View.GONE;
            _containerDuration.visibility = View.VISIBLE;
        }

        if (watchTime != null) {
            _textHeader.text = watchTime;
            _textHeader.visibility = View.VISIBLE;
        } else {
            _textHeader.visibility = View.GONE;
        }

        var metadata = "";
        if (v.video.viewCount > 0)
            metadata += "${v.video.viewCount.toHumanNumber()} " + itemView.context.getString(R.string.views);

        _textMetadata.text = metadata;

        _timeBar.progress = v.position.toFloat() / v.video.duration.toFloat();
        video = v;
    }

    companion object {
        val TAG = "HistoryListViewHolder";
    }
}