package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.futo.platformplayer.*
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.structures.EmptyPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.engine.exceptions.ScriptCaptchaRequiredException
import com.futo.platformplayer.engine.exceptions.ScriptExecutionException
import com.futo.platformplayer.engine.exceptions.ScriptImplementationException
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateMeta
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.NoResultsView
import com.futo.platformplayer.views.adapters.ContentPreviewViewHolder
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import com.futo.platformplayer.views.adapters.InsertedViewHolder
import com.futo.platformplayer.views.announcements.AnnouncementView
import com.futo.platformplayer.views.buttons.BigButton
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime

class HomeFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _view: HomeView? = null;
    private var _cachedRecyclerData: FeedView.RecyclerData<InsertedViewAdapterWithLoader<ContentPreviewViewHolder>, GridLayoutManager, IPager<IPlatformContent>, IPlatformContent, IPlatformContent, InsertedViewHolder<ContentPreviewViewHolder>>? = null;

    fun reloadFeed() {
        _view?.reloadFeed()
    }

    fun scrollToTop(smooth: Boolean) {
        _view?.scrollToTop(smooth)
    }

    override fun onShownWithView(parameter: Any?, isBack: Boolean) {
        super.onShownWithView(parameter, isBack);
        _view?.onShown();
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
        val view = HomeView(this, inflater, _cachedRecyclerData);
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

        val view = _view;
        if (view != null) {
            _cachedRecyclerData = view.recyclerData;
            view.cleanup();
            _view = null;
        }
    }

    fun setPreviewsEnabled(previewsEnabled: Boolean) {
        _view?.setPreviewsEnabled(previewsEnabled && Settings.instance.home.previewFeedItems);
    }

    @SuppressLint("ViewConstructor")
    class HomeView : ContentFeedView<HomeFragment> {
        override val feedStyle: FeedStyle get() = Settings.instance.home.getHomeFeedStyle();

        private val _taskGetPager: TaskHandler<Boolean, IPager<IPlatformContent>>;
        override val shouldShowTimeBar: Boolean get() = Settings.instance.home.progressBar

        constructor(fragment: HomeFragment, inflater: LayoutInflater, cachedRecyclerData: RecyclerData<InsertedViewAdapterWithLoader<ContentPreviewViewHolder>, GridLayoutManager, IPager<IPlatformContent>, IPlatformContent, IPlatformContent, InsertedViewHolder<ContentPreviewViewHolder>>? = null) : super(fragment, inflater, cachedRecyclerData) {
            _taskGetPager = TaskHandler<Boolean, IPager<IPlatformContent>>({ fragment.lifecycleScope }, {
                StatePlatform.instance.getHomeRefresh(fragment.lifecycleScope)
            })
            .success { loadedResult(it); }
            .exception<ScriptCaptchaRequiredException> {  }
            .exception<ScriptExecutionException> {
                Logger.w(ChannelFragment.TAG, "Plugin failure.", it);
                UIDialogs.showDialog(context, R.drawable.ic_error_pred, context.getString(R.string.failed_to_get_home_plugin) + " [${it.config.name}]", it.message, null, 0,
                    UIDialogs.Action(context.getString(R.string.ignore), {}),
                    UIDialogs.Action(context.getString(R.string.sources), { fragment.navigate<SourcesFragment>() }, UIDialogs.ActionStyle.PRIMARY)
                );
            }
            .exception<ScriptImplementationException> {
                Logger.w(TAG, "Plugin failure.", it);
                UIDialogs.showDialog(context, R.drawable.ic_error_pred, context.getString(R.string.failed_to_get_home_plugin) + " [${it.config.name}]", it.message, null, 0,
                    UIDialogs.Action(context.getString(R.string.ignore), {}),
                    UIDialogs.Action(context.getString(R.string.sources), { fragment.navigate<SourcesFragment>() }, UIDialogs.ActionStyle.PRIMARY)
                );
            }
            .exception<Throwable> {
                Logger.w(TAG, "Failed to load channel.", it);
                UIDialogs.showGeneralRetryErrorDialog(context, context.getString(R.string.failed_to_get_home), it, {
                    loadResults()
                }, {
                    finishRefreshLayoutLoader();
                    setLoading(false);
                }, fragment);
            };

            setPreviewsEnabled(Settings.instance.home.previewFeedItems);
            showAnnouncementView()
        }

        fun onShown() {
            val lastClients = recyclerData.lastClients;
            val clients = StatePlatform.instance.getSortedEnabledClient().filter { if (it is JSClient) it.enableInHome else true };
            val feedstyleChanged = recyclerData.loadedFeedStyle != feedStyle;
            val clientsChanged = lastClients == null || lastClients.size != clients.size || !lastClients.containsAll(clients);
            Logger.i(TAG, "onShown (recyclerData.loadedFeedStyle=${recyclerData.loadedFeedStyle}, recyclerData.lastLoad=${recyclerData.lastLoad}, feedstyleChanged=$feedstyleChanged, clientsChanged=$clientsChanged)")

            if(feedstyleChanged || clientsChanged) {
                reloadFeed()
            } else {
                setLoading(false);
            }

            finishRefreshLayoutLoader();
        }

        fun scrollToTop(smooth: Boolean) {
            if (smooth) {
                _recyclerResults.smoothScrollToPosition(0)
            } else {
                _recyclerResults.scrollToPosition(0)
            }
        }

        fun reloadFeed() {
            recyclerData.lastLoad = OffsetDateTime.now();
            recyclerData.loadedFeedStyle = feedStyle;
            recyclerData.lastClients = StatePlatform.instance.getSortedEnabledClient().filter { if (it is JSClient) it.enableInHome else true };
            loadResults();
        }

        override fun getEmptyPagerView(): View {
            val dp10 = 10.dp(resources);
            val dp30 = 30.dp(resources);

            val pluginsExist = StatePlatform.instance.getAvailableClients().isNotEmpty();
            if(StatePlatform.instance.getEnabledClients().isEmpty())
                //Initial setup
                return NoResultsView(context, "No enabled sources", if(pluginsExist)
                        "Enable or install some sources"
                    else "This Grayjay version comes without any sources, install sources externally or using the button below.", R.drawable.ic_sources,
                    listOf(BigButton(context, "Browse Online Sources", "View official sources online", R.drawable.ic_explore) {
                        fragment.navigate<BrowserFragment>(BrowserFragment.NavigateOptions("https://plugins.grayjay.app/phone.html", mapOf(
                            Pair("grayjay") { req ->
                                StateApp.instance.contextOrNull?.let {
                                    if(it is MainActivity) {
                                        runBlocking {
                                            it.handleUrlAll(req.url.toString());
                                        }
                                    }
                                };
                            }
                        )));
                    }.withMargin(dp10, dp30),
                    if(pluginsExist) BigButton(context, "Sources", "Go to the sources tab", R.drawable.ic_creators) {
                        fragment.navigate<SourcesFragment>();
                    }.withMargin(dp10, dp30) else null).filterNotNull()
                );
            else
                return NoResultsView(context, "Nothing to see here", "The enabled sources do not have any results.", R.drawable.ic_help,
                    listOf(BigButton(context, "Sources", "Go to the sources tab", R.drawable.ic_creators) {
                        fragment.navigate<SourcesFragment>();
                    }.withMargin(dp10, dp30))
                )
        }

        override fun reload() {
            loadResults();
        }

        override fun filterResults(results: List<IPlatformContent>): List<IPlatformContent> {
            return results.filter { !StateMeta.instance.isVideoHidden(it.url) && !StateMeta.instance.isCreatorHidden(it.author.url) };
        }

        private fun loadResults() {
            setLoading(true);
            _taskGetPager.run(true);
        }
        private fun loadedResult(pager : IPager<IPlatformContent>) {
            if (pager is EmptyPager<IPlatformContent>) {
                //StateAnnouncement.instance.registerAnnouncement(UUID.randomUUID().toString(), context.getString(R.string.no_home_available), context.getString(R.string.no_home_page_is_available_please_check_if_you_are_connected_to_the_internet_and_refresh), AnnouncementType.SESSION);
            }

            Logger.i(TAG, "Got new home pager $pager");
            finishRefreshLayoutLoader();
            setLoading(false);
            setPager(pager);
            if(pager.getResults().isEmpty() && !pager.hasMorePages())
                setEmptyPager(true);
        }
    }

    companion object {
        const val TAG = "HomeFragment";

        fun newInstance() = HomeFragment().apply {}
    }
}