package com.futo.platformplayer.views.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1

class EnabledSourceAdapter : RecyclerView.Adapter<EnabledSourceViewHolder> {
    private val _sources: MutableList<IPlatformClient>;
    private val _touchHelper: ItemTouchHelper;

    var onRemove = Event1<IPlatformClient>();
    var onClick = Event1<IPlatformClient>();
    var canRemove: Boolean = false;

    constructor(sources: MutableList<IPlatformClient>, touchHelper: ItemTouchHelper) : super() {
        _sources = sources;
        _touchHelper = touchHelper;
    }

    override fun getItemCount() = _sources.size;

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): EnabledSourceViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_source_enabled, viewGroup, false)
        val holder = EnabledSourceViewHolder(view, _touchHelper);
        holder.onRemove.subscribe { onRemove.emit(it); };
        holder.onClick.subscribe { onClick.emit(it); }
        holder.setCanRemove(canRemove);
        return holder;
    }

    override fun onBindViewHolder(viewHolder: EnabledSourceViewHolder, position: Int) {
        viewHolder.setCanRemove(canRemove);
        viewHolder.bind(_sources[position])
    }
}
