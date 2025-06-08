package com.futo.platformplayer.views.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.constructs.Event1

class VideoListEditorAdapter : RecyclerView.Adapter<VideoListEditorViewHolder> {
    private var _videos: ArrayList<IPlatformVideo>? = null;
    private val _touchHelper: ItemTouchHelper;

    val onClick = Event1<IPlatformVideo>();
    val onRemove = Event1<IPlatformVideo>();
    val onOptions = Event1<IPlatformVideo>();
    var canEdit = false
        private set;

    constructor(touchHelper: ItemTouchHelper) : super() {
        _touchHelper = touchHelper;
    }

    override fun getItemCount() = _videos?.size ?: 0;

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): VideoListEditorViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_playlist, viewGroup, false);
        val holder = VideoListEditorViewHolder(view, _touchHelper);

        holder.onRemove.subscribe { v -> onRemove.emit(v); };
        holder.onOptions.subscribe { v -> onOptions.emit(v); };
        holder.onClick.subscribe { v -> onClick.emit(v); };

        return holder;
    }

    override fun onBindViewHolder(viewHolder: VideoListEditorViewHolder, position: Int) {
        val videos = _videos ?: return;
        viewHolder.bind(videos[position], canEdit);
    }

    fun setCanEdit(canEdit: Boolean, notify: Boolean = false) {
        this.canEdit = canEdit;
        if (notify) {
            _videos?.let { notifyItemRangeChanged(0, it.size); };
        }
    }

    fun setVideos(videos: ArrayList<IPlatformVideo>, canEdit: Boolean) {
        _videos = videos;
        setCanEdit(canEdit, false);
        notifyDataSetChanged();
    }
}
