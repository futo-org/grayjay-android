package com.futo.platformplayer.views.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1

class SearchSuggestionAdapter : RecyclerView.Adapter<SearchSuggestionViewHolder> {
    private val _dataset: ArrayList<String>;

    var onAddToQuery = Event1<String>();
    var onClicked = Event1<String>();
    var onRemove = Event1<String>();
    var isHistorical: Boolean = false;

    constructor(dataSet: ArrayList<String>) : super() {
        _dataset = dataSet;
    }

    override fun getItemCount() = _dataset.size;

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): SearchSuggestionViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.list_search_suggestion, viewGroup, false)
        val holder = SearchSuggestionViewHolder(view);
        holder.onAddToQuery.subscribe { suggestion -> onAddToQuery.emit(suggestion); };
        holder.onClicked.subscribe { suggestion -> onClicked.emit(suggestion); };
        holder.onRemove.subscribe { suggestion -> onRemove.emit(suggestion); };
        return holder;
    }
    override fun onBindViewHolder(viewHolder: SearchSuggestionViewHolder, position: Int) {
        viewHolder.bind(_dataset[position], isHistorical);
    }
}
