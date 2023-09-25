package com.futo.platformplayer.views.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.constructs.Event1

class VideoListHorizontalAdapter : RecyclerView.Adapter<VideoListHorizontalViewHolder> {
    private val _dataset: ArrayList<IPlatformVideo>;

    val onClick = Event1<IPlatformVideo>();

    constructor(dataset: ArrayList<IPlatformVideo>) : super() {
        _dataset = dataset;
    }

    override fun getItemCount() = _dataset.size;

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): VideoListHorizontalViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_video_horizontal, viewGroup, false);
        val holder = VideoListHorizontalViewHolder(view);
        holder.onClick.subscribe {
            val video = holder.video;
            if (video != null)
                onClick.emit(video);
        };

        return holder;
    }

    override fun onBindViewHolder(viewHolder: VideoListHorizontalViewHolder, position: Int) {
        viewHolder.bind(_dataset[position])
    }
}
