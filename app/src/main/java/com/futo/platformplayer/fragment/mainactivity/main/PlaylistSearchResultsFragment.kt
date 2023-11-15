package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.fragment.mainactivity.topbar.SearchTopBarFragment
import com.futo.platformplayer.views.FeedStyle

class PlaylistSearchResultsFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = false;
    override val hasBottomBar: Boolean get() = true;

    private var _view: PlaylistSearchResultsView? = null;

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        _view?.onShown(parameter, isBack);
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
        val view = PlaylistSearchResultsView(this, inflater);
        _view = view;
        return view;
    }

    override fun onBackPressed(): Boolean {
        if (_view?.onBackPressed() == true)
            return true

        return super.onBackPressed()
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView();
        _view?.cleanup();
        _view = null;
    }

    @SuppressLint("ViewConstructor")
    class PlaylistSearchResultsView : ContentFeedView<PlaylistSearchResultsFragment> {
        override val feedStyle: FeedStyle get() = Settings.instance.search.getSearchFeedStyle();

        private var _query: String? = null;

        private val _taskSearch: TaskHandler<String, IPager<IPlatformContent>>;
        constructor(fragment: PlaylistSearchResultsFragment, inflater: LayoutInflater) : super(fragment, inflater) {
            _taskSearch = TaskHandler<String, IPager<IPlatformContent>>({fragment.lifecycleScope}, { query -> StatePlatform.instance.searchPlaylist(query) })
                .success { loadedResult(it); }
                .exception<Throwable> {
                    Logger.w(ChannelFragment.TAG, "Failed to load results.", it);
                    UIDialogs.showGeneralRetryErrorDialog(context, it.message ?: "", it, { loadResults() });
                }
        }

        override fun cleanup() {
            super.cleanup();
            _taskSearch.cancel();
        }

        fun onShown(parameter: Any?, isBack: Boolean) {
            if(parameter is String) {
                setQuery(parameter);

                fragment.topBar?.apply {
                    if (this is SearchTopBarFragment) {
                        setText(parameter);
                        onSearch.subscribe(this) {
                            setQuery(it);
                        };
                    }
                }
            }
        }

        override fun reload() {
            loadResults();
        }

        private fun setQuery(query: String) {
            clearResults();
            _query = query;
            loadResults();
        }

        private fun loadResults() {
            val query = _query;
            if (query.isNullOrBlank()) {
                return;
            }

            setLoading(true);
            _taskSearch.run(query);
        }
        private fun loadedResult(pager: IPager<IPlatformContent>) {
            finishRefreshLayoutLoader();
            setLoading(false);
            setPager(pager);
        }
    }

    companion object {
        fun newInstance() = PlaylistSearchResultsFragment().apply {}
    }
}