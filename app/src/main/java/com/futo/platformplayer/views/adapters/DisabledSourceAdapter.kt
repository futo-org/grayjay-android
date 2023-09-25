package com.futo.platformplayer.views.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.constructs.Event1

class DisabledSourceAdapter : RecyclerView.Adapter<DisabledSourceViewHolder> {
    private val _sources: MutableList<IPlatformClient>;

    var onClick = Event1<IPlatformClient>();
    var onAdd = Event1<IPlatformClient>();

    constructor(sources: MutableList<IPlatformClient>) : super() {
        _sources = sources;
    }

    override fun getItemCount() = _sources.size

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): DisabledSourceViewHolder {
        val holder = DisabledSourceViewHolder(viewGroup);
        holder.onAdd.subscribe {
            val source = holder.source;
            if (source != null) {
                onAdd.emit(source);
            }
        }
        holder.onClick.subscribe {
            val source = holder.source;
            if (source != null) {
                onClick.emit(source);
            }
        };
        return holder;
    }

    override fun onBindViewHolder(viewHolder: DisabledSourceViewHolder, position: Int) {
        viewHolder.bind(_sources[position])
    }
}
