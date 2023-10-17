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
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.engine.exceptions.ScriptCaptchaRequiredException
import com.futo.platformplayer.fragment.mainactivity.topbar.SearchTopBarFragment
import com.futo.platformplayer.views.FeedStyle

class CreatorSearchResultsFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = false;
    override val hasBottomBar: Boolean get() = true;

    private var _view: CreatorSearchResultsView? = null;

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        _view?.onShown(parameter, isBack);
    }

    override fun onResume() {
        super.onResume()
        _view?.onResume();
    }

    override fun onCreateMainView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = CreatorSearchResultsView(this, inflater);
        _view = view;
        return view;
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView();
        _view?.cleanup();
        _view = null;
    }

    @SuppressLint("ViewConstructor")
    class CreatorSearchResultsView : CreatorFeedView<CreatorSearchResultsFragment> {
        override val feedStyle: FeedStyle get() = Settings.instance.search.getSearchFeedStyle();

        private var _query: String? = null;

        private val _taskSearch: TaskHandler<String, IPager<PlatformAuthorLink>>;

        constructor(fragment: CreatorSearchResultsFragment, inflater: LayoutInflater): super(fragment, inflater) {
            _taskSearch = TaskHandler<String, IPager<PlatformAuthorLink>>({fragment.lifecycleScope}, { query -> StatePlatform.instance.searchChannels(query) })
                .success { loadedResult(it); }
                .exception<ScriptCaptchaRequiredException> {  }
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
                if(!isBack) {
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
        private fun loadedResult(pager: IPager<PlatformAuthorLink>) {
            finishRefreshLayoutLoader();
            setLoading(false);
            setPager(pager);
        }
    }

    companion object {
        fun newInstance() = CreatorSearchResultsFragment().apply {}
    }
}