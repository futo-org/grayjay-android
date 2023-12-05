package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.*
import com.futo.platformplayer.states.StateHistory
import com.futo.platformplayer.states.StatePlayer
import com.futo.platformplayer.states.StatePlaylists
import com.futo.platformplayer.views.others.TagsView
import com.futo.platformplayer.views.adapters.HistoryListAdapter

class HistoryFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _adapter: HistoryListAdapter? = null;

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_history, container, false);

        val inputMethodManager = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager;

        val recyclerHistory = view.findViewById<RecyclerView>(R.id.recycler_history);
        val clearSearch = view.findViewById<ImageButton>(R.id.button_clear_search);
        val editSearch = view.findViewById<EditText>(R.id.edit_search);
        var tagsView = view.findViewById<TagsView>(R.id.tags_text);
        tagsView.setPairs(listOf(
            Pair(getString(R.string.last_hour), 60L),
            Pair(getString(R.string.last_24_hours), 24L * 60L),
            Pair(getString(R.string.last_week), 7L * 24L * 60L),
            Pair(getString(R.string.last_30_days), 30L * 24L * 60L),
            Pair(getString(R.string.last_year), 365L * 30L * 24L * 60L),
            Pair(getString(R.string.all_time), -1L)));

        val adapter = HistoryListAdapter();
        adapter.onClick.subscribe { v ->
            val diff = v.video.duration - v.position;
            val vid: Any = if (diff > 5) { v.video.withTimestamp(v.position) } else { v.video };
            StatePlayer.instance.clearQueue();
            navigate<VideoDetailFragment>(vid).maximizeVideoDetail();
            editSearch.clearFocus();
            inputMethodManager.hideSoftInputFromWindow(editSearch.windowToken, 0);
        };
        _adapter = adapter;

        recyclerHistory.adapter = adapter;
        recyclerHistory.isSaveEnabled = false;
        recyclerHistory.layoutManager = LinearLayoutManager(context);

        tagsView.onClick.subscribe { timeMinutesToErase ->
            UIDialogs.showConfirmationDialog(requireContext(), getString(R.string.are_you_sure_delete_historical), {
                StateHistory.instance.removeHistoryRange(timeMinutesToErase.second as Long);
                UIDialogs.toast(view.context, timeMinutesToErase.first + " " + getString(R.string.removed));
                adapter.updateFilteredVideos();
                adapter.notifyDataSetChanged();
            });
        };

        clearSearch.setOnClickListener {
            editSearch.text.clear();
            clearSearch.visibility = View.GONE;
            adapter.setQuery("");
            editSearch.clearFocus();
            inputMethodManager.hideSoftInputFromWindow(editSearch.windowToken, 0);
        };

        editSearch.addTextChangedListener { _ ->
            val text = editSearch.text;
            clearSearch.visibility = if (text.isEmpty()) { View.GONE } else { View.VISIBLE };
            adapter.setQuery(text.toString());
        };

        return view;
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView();
        _adapter?.cleanup();
        _adapter = null;
    }

    companion object {
        fun newInstance() = HistoryFragment().apply {}
    }
}