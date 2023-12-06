package com.futo.platformplayer.views.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.*
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.models.HistoryVideo
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateHistory
import com.futo.platformplayer.states.StatePlaylists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class HistoryListAdapter : RecyclerView.Adapter<HistoryListViewHolder> {
    private lateinit var _filteredVideos: MutableList<HistoryVideo>;

    val onClick = Event1<HistoryVideo>();
    private var _query: String = "";

    constructor() : super() {
        updateFilteredVideos();

        StateHistory.instance.onHistoricVideoChanged.subscribe(this) { video, position ->
            StateApp.instance.scope.launch(Dispatchers.Main) {
                val index = _filteredVideos.indexOfFirst { v -> v.video.url == video.url };
                if (index == -1) {
                    return@launch;
                }

                _filteredVideos[index].position = position;
                if (index < _filteredVideos.size - 2) {
                    notifyItemRangeChanged(index, 2);
                } else {
                    notifyItemChanged(index);
                }
            }
        };
    }

    fun setQuery(query: String) {
        _query = query;
        updateFilteredVideos();
    }

    fun updateFilteredVideos() {
        val videos = StateHistory.instance.getHistory();
        //filtered val pager = StateHistory.instance.getHistorySearchPager("querrryyyyy"); TODO: Implement pager

        if (_query.isBlank()) {
            _filteredVideos = videos.toMutableList();
        } else {
            _filteredVideos = videos.filter { v -> v.video.name.lowercase().contains(_query); }.toMutableList();
        }

        notifyDataSetChanged();
    }

    fun cleanup() {
        StateHistory.instance.onHistoricVideoChanged.remove(this);
    }

    override fun getItemCount() = _filteredVideos.size;

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): HistoryListViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_history, viewGroup, false);
        val holder = HistoryListViewHolder(view);

        holder.onRemove.subscribe { v ->
            val videos = _filteredVideos;
            val index = videos.indexOf(v);
            if (index == -1) {
                return@subscribe;
            }

            StateHistory.instance.removeHistory(v.video.url);
            _filteredVideos.removeAt(index);
            notifyItemRemoved(index);
        };
        holder.onClick.subscribe { v ->
            val videos = _filteredVideos;
            val index = videos.indexOf(v);
            if (index == -1) {
                return@subscribe;
            }

            _filteredVideos.removeAt(index);
            _filteredVideos.add(0, v);

            notifyItemMoved(index, 0);
            notifyItemRangeChanged(0, 2);
            onClick.emit(v);
        };

        return holder;
    }

    override fun onBindViewHolder(viewHolder: HistoryListViewHolder, position: Int) {
        val videos = _filteredVideos;
        var watchTime: String? = null;
        if (position == 0) {
            watchTime = videos[position].date.toHumanNowDiffStringMinDay();
        } else {
            val previousWatchTime = videos[position - 1].date.toHumanNowDiffStringMinDay();
            val currentWatchTime = videos[position].date.toHumanNowDiffStringMinDay();
            if (previousWatchTime != currentWatchTime) {
                watchTime = currentWatchTime;
            }
        }

        viewHolder.bind(videos[position], watchTime);
    }

    companion object {
        val TAG = "HistoryListAdapter";
    }
}
