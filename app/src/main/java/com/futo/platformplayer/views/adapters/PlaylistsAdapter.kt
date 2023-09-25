package com.futo.platformplayer.views.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.models.Playlist

class PlaylistsAdapter : RecyclerView.Adapter<PlaylistsViewHolder> {
    private val _dataset: ArrayList<Playlist>;

    val onClick = Event1<Playlist>();
    val onPlay = Event1<Playlist>();
    val onRemoved = Event1<Playlist>();

    private val _inflater: LayoutInflater;
    private val _deletionConfirmationMessage: String;

    constructor(dataset: ArrayList<Playlist>, inflater: LayoutInflater, deletionConfirmationMessage: String) : super() {
        _dataset = dataset;
        _inflater = inflater;
        _deletionConfirmationMessage = deletionConfirmationMessage;
    }

    override fun getItemCount() = _dataset.size;

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): PlaylistsViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_playlists, viewGroup, false);
        val holder = PlaylistsViewHolder(view);
        holder.onClick.subscribe {
            val playlist = holder.playlist;
            if (playlist != null)
                onClick.emit(playlist);
        };

        holder.onPlay.subscribe {
            val playlist = holder.playlist;
            if (playlist != null) {
                onPlay.emit(playlist);
            }
        };

        holder.onRemove.subscribe {
            val playlist = holder.playlist;
            if (playlist != null) {
                UIDialogs.showConfirmationDialog(_inflater.context, _deletionConfirmationMessage, {
                    val index = _dataset.indexOf(playlist);
                    if (index >= 0) {
                        _dataset.removeAt(index);
                        notifyItemRemoved(index);
                        onRemoved.emit(playlist);
                    }

                    StatePlaylists.instance.removePlaylist(playlist);
                });
            }
        };

        return holder;
    }

    override fun onBindViewHolder(viewHolder: PlaylistsViewHolder, position: Int) {
        viewHolder.bind(_dataset[position])
    }
}
