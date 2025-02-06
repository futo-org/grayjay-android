package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.UISlideOverlays
import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.engine.exceptions.ScriptCaptchaRequiredException
import com.futo.platformplayer.fragment.mainactivity.topbar.SearchTopBarFragment
import com.futo.platformplayer.isHttpUrl
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateMeta
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.views.FeedStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContentSearchResultsFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = false;
    override val hasBottomBar: Boolean get() = true;

    private var _view: ContentSearchResultsView? = null;

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        _view?.onShown(parameter);
    }

    override fun onHide() {
        super.onHide()
        _view?.onHide();
    }

    override fun onResume() {
        super.onResume()
        _view?.onResume();
    }

    override fun onPause() {
        super.onPause()
        _view?.onPause();
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = ContentSearchResultsView(this, inflater);
        _view = view;
        return view;
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView();
        _view?.cleanup();
        _view = null;
    }

    override fun onBackPressed(): Boolean {
        if (_view?.onBackPressed() == true)
            return true

        return super.onBackPressed()
    }

    fun setPreviewsEnabled(previewsEnabled: Boolean) {
        _view?.setPreviewsEnabled(previewsEnabled && Settings.instance.search.previewFeedItems);
    }

    @SuppressLint("ViewConstructor")
    class ContentSearchResultsView : ContentFeedView<ContentSearchResultsFragment> {
        override val feedStyle: FeedStyle get() = Settings.instance.search.getSearchFeedStyle();

        private var _query: String? = null;
        private var _sortBy: String? = null;
        private var _filterValues: HashMap<String, List<String>> = hashMapOf();
        private var _enabledClientIds: List<String>? = null;
        private var _channelUrl: String? = null;

        private val _taskSearch: TaskHandler<String, IPager<IPlatformContent>>;
        override val shouldShowTimeBar: Boolean get() = Settings.instance.search.progressBar

        constructor(fragment: ContentSearchResultsFragment, inflater: LayoutInflater) : super(fragment, inflater) {
            _taskSearch = TaskHandler<String, IPager<IPlatformContent>>({fragment.lifecycleScope}, { query ->
                Logger.i(TAG, "Searching for: $query")
                val channelUrl = _channelUrl;
                if (channelUrl != null) {
                    StatePlatform.instance.searchChannel(channelUrl, query, null, _sortBy, _filterValues, _enabledClientIds)
                } else {
                    StatePlatform.instance.searchRefresh(fragment.lifecycleScope, query, null, _sortBy, _filterValues, _enabledClientIds)
                }
            })
            .success { loadedResult(it); }.exception<ScriptCaptchaRequiredException> {  }
            .exception<Throwable> {
                Logger.w(TAG, "Failed to load results.", it);
                UIDialogs.showGeneralRetryErrorDialog(context, it.message ?: "", it, { loadResults() }, null, fragment);
            }

            setPreviewsEnabled(Settings.instance.search.previewFeedItems);
        }

        override fun cleanup() {
            super.cleanup();
            _taskSearch.cancel();
        }

        fun onShown(parameter: Any?) {
            if(parameter is SuggestionsFragmentData) {
                setQuery(parameter.query, false);
                setChannelUrl(parameter.channelUrl, false);

                fragment.topBar?.apply {
                    if (this is SearchTopBarFragment) {
                        this.setText(parameter.query);
                    }
                }
            }

            fragment.topBar?.apply {
                if (this is SearchTopBarFragment) {
                    setFilterButtonVisible(true);

                    onFilterClick.subscribe(this) {
                        _overlayContainer.let {
                            val filterValuesCopy = HashMap(_filterValues);
                            val filtersOverlay = UISlideOverlays.showFiltersOverlay(lifecycleScope, it, _enabledClientIds!!, filterValuesCopy, _channelUrl != null);
                            filtersOverlay.onOK.subscribe { enabledClientIds, changed ->
                                if (changed) {
                                    setFilterValues(filtersOverlay.commonCapabilities, filterValuesCopy);
                                }

                                _enabledClientIds = enabledClientIds;

                                val sorts = filtersOverlay.commonCapabilities?.sorts ?: listOf();
                                if (sorts.isNotEmpty()) {
                                    setSortByOptions(sorts);
                                    if (!sorts.contains(_sortBy)) {
                                        _sortBy = null;
                                    }
                                } else {
                                    setSortByOptions(null);
                                    _sortBy = null;
                                }

                                loadResults();
                            };
                        };
                    };

                    onSearch.subscribe(this) {
                        if(it.isHttpUrl()) {
                            if(StatePlatform.instance.hasEnabledPlaylistClient(it))
                                navigate<RemotePlaylistFragment>(it);
                            else if(StatePlatform.instance.hasEnabledChannelClient(it))
                                navigate<ChannelFragment>(it);
                            else
                                navigate<VideoDetailFragment>(it);
                        }
                        else
                            setQuery(it, true);
                    };
                }
            }

            fragment.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val commonCapabilities =
                        if(_channelUrl == null)
                            StatePlatform.instance.getCommonSearchCapabilities(StatePlatform.instance.getEnabledClients().map { it.id });
                        else
                            StatePlatform.instance.getCommonSearchChannelContentsCapabilities(StatePlatform.instance.getEnabledClients().map { it.id });
                    val sorts = commonCapabilities?.sorts ?: listOf();
                    if (sorts.size > 1) {
                        withContext(Dispatchers.Main) {
                            try {
                                setSortByOptions(sorts);
                            } catch (e: Throwable) {
                                Logger.e(TAG, "Failed to set sort options.", e);
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to gtet common search capabilities.", e)
                }
            }

            onSortBySelect.subscribe(this) {
                if (_sortBy == it) {
                    return@subscribe;
                }

                Logger.i(TAG, "Sort by changed: $it")
                setSortBy(it);
            };

            _enabledClientIds = StatePlatform.instance.getEnabledClients().map { it -> it.id };
            _sortBy = null;
            _filterValues.clear();

            clearResults();
            loadResults();
        }

        fun onHide() {
            onSortBySelect.remove(this);

            fragment.topBar?.apply {
                if (this is SearchTopBarFragment) {
                    setFilterButtonVisible(false);
                    onFilterClick.remove(this);
                    onSearch.remove(this);
                }
            };

            setActiveTags(null);
            setSortByOptions(null);
        }

        override fun filterResults(results: List<IPlatformContent>): List<IPlatformContent> {
            if(Settings.instance.search.hidefromSearch)
                return super.filterResults(results.filter { !StateMeta.instance.isVideoHidden(it.url) && !StateMeta.instance.isCreatorHidden(it.author.url) });
            return super.filterResults(results)
        }

        override fun reload() {
            loadResults();
        }

        private fun setQuery(query: String, updateResults: Boolean = true) {
            _query = query;

            if (updateResults) {
                clearResults();
                loadResults();
            }
        }

        private fun setChannelUrl(channelUrl: String?, updateResults: Boolean = true) {
            _channelUrl = channelUrl;

            if (updateResults) {
                clearResults();
                loadResults();
            }
        }

        private fun setSortBy(sortBy: String?, updateResults: Boolean = true) {
            _sortBy = sortBy;

            if (updateResults) {
                clearResults();
                loadResults();
            }
        }

        private fun setFilterValues(resultCapabilities: ResultCapabilities?, filterValues: HashMap<String, List<String>>) {
            clearResults();

            if (resultCapabilities != null) {
                val tags = arrayListOf<String>();
                for (filter in resultCapabilities.filters) {
                    val values = filterValues[filter.idOrName] ?: continue;
                    if (values.isNotEmpty()) {
                        val titles = arrayListOf<String>();
                        for (value in values) {
                            val title = filter.filters.firstOrNull { it.idOrName == value } ?: continue;
                            titles.add(title.idOrName);
                        }

                        tags.add("${filter.name}: ${titles.joinToString(", ")}");
                    }
                }

                setActiveTags(tags);
            } else {
                setActiveTags(null);
            }

            _filterValues = filterValues;
            loadResults();
        }

        override fun onContentClicked(content: IPlatformContent, time: Long) {
            super.onContentClicked(content, time)

            (fragment.topBar as SearchTopBarFragment?)?.apply {
                clearFocus();
            }
        }

        private fun loadResults() {
            val query = _query;
            if (query.isNullOrBlank()) {
                return;
            }

            setLoading(true);
            _taskSearch.run(query);
        }
        private fun loadedResult(pager : IPager<IPlatformContent>) {
            finishRefreshLayoutLoader();
            setLoading(false);
            setPager(pager);
        }
    }

    companion object {
        private const val TAG = "VideoSearchResultsFragment";

        fun newInstance() = ContentSearchResultsFragment().apply {}
    }
}