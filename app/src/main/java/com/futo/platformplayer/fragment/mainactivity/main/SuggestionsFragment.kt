package com.futo.platformplayer.fragment.mainactivity.main

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.platformplayer.*
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.fragment.mainactivity.topbar.SearchTopBarFragment
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.SearchType
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.SearchHistoryStorage
import com.futo.platformplayer.views.adapters.SearchSuggestionAdapter

data class SuggestionsFragmentData(val query: String, val searchType: SearchType, val channelUrl: String? = null);

class SuggestionsFragment : MainFragment {
    override val isMainView : Boolean = true;
    override val hasBottomBar: Boolean = false;
    override val isHistory: Boolean = false;

    private var  _recyclerSuggestions: RecyclerView? = null;
    private var _llmSuggestions: LinearLayoutManager? = null;
    private val _suggestions: ArrayList<String> = ArrayList();
    private var _query: String? = null;
    private var _searchType: SearchType = SearchType.VIDEO;
    private var _channelUrl: String? = null;

    private val _adapterSuggestions = SearchSuggestionAdapter(_suggestions);

    private val _getSuggestions = TaskHandler<String, Array<String>>({lifecycleScope}, {
            query -> StatePlatform.instance.searchSuggestions(query)
    })
    .success { suggestions -> updateSuggestions(suggestions, false) }
    .exception<Throwable> {
        Logger.w(ChannelFragment.TAG, "Failed to load suggestions.", it);
        UIDialogs.showGeneralRetryErrorDialog(requireContext(), it.message ?: "", it, { loadSuggestions() }, null, this);
    };

    constructor(): super() {
        _adapterSuggestions.onAddToQuery.subscribe { suggestion -> (topBar as SearchTopBarFragment?)?.setText(suggestion); };
        _adapterSuggestions.onClicked.subscribe { suggestion ->
            val storage = FragmentedStorage.get<SearchHistoryStorage>();
            storage.add(suggestion);

            if (_searchType == SearchType.CREATOR) {
                navigate<CreatorSearchResultsFragment>(suggestion);
            } else if (_searchType == SearchType.PLAYLIST) {
                navigate<PlaylistSearchResultsFragment>(suggestion);
            } else {
                navigate<ContentSearchResultsFragment>(SuggestionsFragmentData(suggestion, SearchType.VIDEO, _channelUrl));
            }
        }
        _adapterSuggestions.onRemove.subscribe { suggestion ->
            val index = _suggestions.indexOf(suggestion);
            if (index == -1) {
                return@subscribe;
            }

            val storage = FragmentedStorage.get<SearchHistoryStorage>();
            storage.lastQueries.removeAt(index);
            _suggestions.removeAt(index);
            _adapterSuggestions.notifyItemRemoved(index);
            storage.save();
        };
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_suggestion_list, container, false);

        val recyclerSuggestions: RecyclerView = view.findViewById(R.id.list_suggestions);
        recyclerSuggestions.layoutManager = _llmSuggestions;
        recyclerSuggestions.adapter = _adapterSuggestions;
        _recyclerSuggestions = recyclerSuggestions;

        loadSuggestions();
        return view;
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        val llmSuggestions = LinearLayoutManager(context);
        llmSuggestions.orientation = LinearLayoutManager.VERTICAL;
        _llmSuggestions = llmSuggestions;
    }

    override fun onDetach() {
        super.onDetach();
        _llmSuggestions = null;
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);

        loadSuggestions();

        if (parameter is SuggestionsFragmentData) {
            _searchType = parameter.searchType;
            _channelUrl = parameter.channelUrl;
        } else if (parameter is SearchType) {
            _searchType = parameter;
            _channelUrl = null;
        }

        topBar?.apply {
            if (this is SearchTopBarFragment) {
                onSearch.subscribe(this) {
                    if (_searchType == SearchType.CREATOR) {
                        navigate<CreatorSearchResultsFragment>(it);
                    } else if (_searchType == SearchType.PLAYLIST) {
                        navigate<PlaylistSearchResultsFragment>(it);
                    } else {
                        if(it.isHttpUrl()) {
                            if(StatePlatform.instance.hasEnabledPlaylistClient(it))
                                navigate<RemotePlaylistFragment>(it);
                            else if(StatePlatform.instance.hasEnabledChannelClient(it))
                                navigate<ChannelFragment>(it);
                            else {
                                val url = it;
                                activity?.let {
                                    close()
                                    if(it is MainActivity)
                                        it.navigate(it.getFragment<VideoDetailFragment>(), url);
                                }
                            }
                        }
                        else
                            navigate<ContentSearchResultsFragment>(SuggestionsFragmentData(it, SearchType.VIDEO, _channelUrl));
                    }
                };

                onTextChange.subscribe(this) {
                    setQuery(it);
                };
            }
        }
    }

    override fun onHide() {
        _query = null;
        updateSuggestions(arrayOf(), false);

        topBar?.apply {
            if (this is SearchTopBarFragment) {
                onSearch.remove(this);
                onTextChange.remove(this);
            }
        }
    }
    private fun setQuery(query: String) {
        _query = query;
        loadSuggestions();
    }

    private fun loadSuggestions() {
        _getSuggestions.cancel();

        val query = _query;
        Logger.i(TAG, "loadSuggestions query='$query'");

        if (query.isNullOrBlank()) {
            if (!Settings.instance.search.searchHistory) {
                updateSuggestions(arrayOf(), false);
                return;
            }

            val lastQueries = FragmentedStorage.get<SearchHistoryStorage>().lastQueries.toTypedArray();
            updateSuggestions(lastQueries, true);
            return;
        }

        _getSuggestions.run(query);
    }

    private fun updateSuggestions(suggestions: Array<String>, isHistorical: Boolean) {
        Logger.i(TAG, "updateSuggestions suggestions='${suggestions.size}' isHistorical=${isHistorical}");

        _suggestions.clear();
        if (suggestions.isNotEmpty()) {
            _suggestions.addAll(suggestions);
        }

        _adapterSuggestions.isHistorical = isHistorical;
        _adapterSuggestions.notifyDataSetChanged();
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView();
        _getSuggestions.onError.clear();
        _recyclerSuggestions = null;
    }

    override fun onDestroy() {
        super.onDestroy();
        _getSuggestions.cancel();
    }

    companion object {
        val TAG = "SuggestionsFragment";

        fun newInstance() = SuggestionsFragment().apply { }
    }
}