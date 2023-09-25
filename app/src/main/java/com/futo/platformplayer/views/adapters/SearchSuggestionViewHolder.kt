package com.futo.platformplayer.views.adapters

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event1

class SearchSuggestionViewHolder : ViewHolder {
    private val _textSuggestion: TextView
    private val _buttonAddToQuery: ImageButton
    private val _buttonRemove: ImageButton;

    var onAddToQuery = Event1<String>();
    var onClicked = Event1<String>();
    var onRemove = Event1<String>();

    var suggestion: String? = null
        private set;

    constructor(view: View) : super(view) {
        _textSuggestion = view.findViewById(R.id.text_suggestion);
        _buttonAddToQuery = view.findViewById(R.id.button_add_to_query);
        _buttonRemove = view.findViewById(R.id.button_remove);

        _buttonAddToQuery.setOnClickListener {
            suggestion?.let { it1 -> onAddToQuery.emit(it1) };
        };
        _buttonRemove.setOnClickListener {
            suggestion?.let { it1 -> onRemove.emit(it1) }
        };
        view.setOnClickListener {
            suggestion?.let { it1 -> onClicked.emit(it1) };
        };
    }

    fun bind(suggestion: String, isHistorical: Boolean) {
        this.suggestion = suggestion;
        _textSuggestion.text = suggestion;
        _buttonRemove.visibility = if (isHistorical) View.VISIBLE else View.GONE;
    }
}