package com.futo.platformplayer.fragment.mainactivity.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.allViews
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.futo.platformplayer.*
import com.futo.platformplayer.UISlideOverlays.Companion.showOrderOverlay
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.structures.EmptyPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.api.media.structures.IRefreshPager
import com.futo.platformplayer.api.media.structures.IReusablePager
import com.futo.platformplayer.api.media.structures.ReusablePager
import com.futo.platformplayer.api.media.structures.ReusableRefreshPager
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.engine.exceptions.ScriptCaptchaRequiredException
import com.futo.platformplayer.engine.exceptions.ScriptExecutionException
import com.futo.platformplayer.engine.exceptions.ScriptImplementationException
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateHistory
import com.futo.platformplayer.states.StateMeta
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringArrayStorage
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.NoResultsView
import com.futo.platformplayer.views.ToggleBar
import com.futo.platformplayer.views.adapters.ContentPreviewViewHolder
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import com.futo.platformplayer.views.adapters.InsertedViewHolder
import com.futo.platformplayer.views.buttons.BigButton
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime

class HomeFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _view: HomeView? = null;
    private var _cachedRecyclerData: FeedView.RecyclerData<InsertedViewAdapterWithLoader<ContentPreviewViewHolder>, GridLayoutManager, IPager<IPlatformContent>, IPlatformContent, IPlatformContent, InsertedViewHolder<ContentPreviewViewHolder>>? = null;
    private var _cachedLastPager: IReusablePager<IPlatformContent>? = null

    private var _toggleRecent = false;
    private var _toggleWatched = false;
    private var _togglePluginsDisabled = mutableListOf<String>();


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
        val view = HomeView(this, inflater, _cachedRecyclerData, _cachedLastPager);
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
            _cachedLastPager = view.lastPager;
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

        private var _toggleBar: ToggleBar? = null;

        private val _taskGetPager: TaskHandler<Boolean, IPager<IPlatformContent>>;
        override val shouldShowTimeBar: Boolean get() = Settings.instance.home.progressBar

        var lastPager: IReusablePager<IPlatformContent>? = null;

        constructor(fragment: HomeFragment, inflater: LayoutInflater, cachedRecyclerData: RecyclerData<InsertedViewAdapterWithLoader<ContentPreviewViewHolder>, GridLayoutManager, IPager<IPlatformContent>, IPlatformContent, IPlatformContent, InsertedViewHolder<ContentPreviewViewHolder>>? = null, cachedLastPager: IReusablePager<IPlatformContent>? = null) : super(fragment, inflater, cachedRecyclerData) {
            lastPager = cachedLastPager
            _taskGetPager = TaskHandler<Boolean, IPager<IPlatformContent>>({ fragment.lifecycleScope }, {
                StatePlatform.instance.getHomeRefresh(fragment.lifecycleScope)
            })
            .success {
                val wrappedPager = if(it is IRefreshPager)
                    ReusableRefreshPager(it);
                else
                    ReusablePager(it);
                lastPager = wrappedPager;
                resetAutomaticNextPageCounter();
                loadedResult(wrappedPager.getWindow());
            }
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

            initializeToolbarContent();

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

        private val _filterLock = Object();
        private var _togglesConfig = FragmentedStorage.get<StringArrayStorage>("home_toggles");
        fun initializeToolbarContent() {
            if(_toolbarContentView.allViews.any { it is ToggleBar })
                _toolbarContentView.removeView(_toolbarContentView.allViews.find { it is ToggleBar });

            if(Settings.instance.home.showHomeFilters) {

                if (!_togglesConfig.any()) {
                    _togglesConfig.set("today", "watched", "plugins");
                    _togglesConfig.save();
                }
                _toggleBar = ToggleBar(context).apply {
                    layoutParams =
                        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                }

                synchronized(_filterLock) {
                    var buttonsPlugins: List<ToggleBar.Toggle> = listOf()
                    buttonsPlugins = (if (_togglesConfig.contains("plugins"))
                        (StatePlatform.instance.getEnabledClients()
                            .filter { it is JSClient && it.enableInHome }
                            .map { plugin ->
                                ToggleBar.Toggle(if(Settings.instance.home.showHomeFiltersPluginNames) plugin.name else "", plugin.icon, !fragment._togglePluginsDisabled.contains(plugin.id), { view, active ->
                                    var dontSwap = false;
                                    if (active) {
                                        if (fragment._togglePluginsDisabled.contains(plugin.id))
                                            fragment._togglePluginsDisabled.remove(plugin.id);
                                    } else {
                                        if (!fragment._togglePluginsDisabled.contains(plugin.id)) {
                                            val enabledClients = StatePlatform.instance.getEnabledClients();
                                            val availableAfterDisable = enabledClients.count { !fragment._togglePluginsDisabled.contains(it.id) && it.id != plugin.id };
                                            if(availableAfterDisable > 0)
                                                fragment._togglePluginsDisabled.add(plugin.id);
                                            else {
                                                UIDialogs.appToast("Home needs atleast 1 plugin active");
                                                dontSwap = true;
                                            }
                                        }
                                    }
                                    if(!dontSwap)
                                        reloadForFilters();
                                    else {
                                        view.setToggle(!active);
                                    }
                                }, { view, views, enabled ->
                                    val toDisable = views.filter { it != view && it.tag == "plugins" };
                                    if(!view.isActive)
                                        view.handleClick();
                                    for(tag in toDisable) {
                                        if(tag.isActive)
                                            tag.handleClick();
                                    }
                                }).withTag("plugins")
                            })
                    else listOf())
                    val buttons = (listOf<ToggleBar.Toggle?>(
                        (if (_togglesConfig.contains("today"))
                            ToggleBar.Toggle("Today", fragment._toggleRecent) { view, active ->
                                fragment._toggleRecent = active; reloadForFilters()
                            }
                                .withTag("today") else null),
                        (if (_togglesConfig.contains("watched"))
                            ToggleBar.Toggle("Unwatched", fragment._toggleWatched) { view, active ->
                                fragment._toggleWatched = active; reloadForFilters()
                            }
                                .withTag("watched") else null),
                    ).filterNotNull() + buttonsPlugins)
                        .sortedBy { _togglesConfig.indexOf(it.tag ?: "") } ?: listOf()

                    val buttonSettings = ToggleBar.Toggle("", R.drawable.ic_settings, true, { view, active ->
                        showOrderOverlay(_overlayContainer,
                            "Visible home filters",
                            listOf(
                                Pair("Plugins", "plugins"),
                                Pair("Today", "today"),
                                Pair("Watched", "watched")
                            ),
                            {
                                val newArray = it.map { it.toString() }.toTypedArray();
                                _togglesConfig.set(*(if (newArray.any()) newArray else arrayOf("none")));
                                _togglesConfig.save();
                                initializeToolbarContent();
                            },
                            "Select which toggles you want to see in order. You can also choose to hide filters in the Grayjay Settings"
                        );
                    }).asButton();

                    val buttonsOrder = (buttons + listOf(buttonSettings)).toTypedArray();
                    _toggleBar?.setToggles(*buttonsOrder);
                }

                _toolbarContentView.addView(_toggleBar, 0);
            }
        }
        fun reloadForFilters() {
            lastPager?.let { loadedResult(it.getWindow()) };
        }

        override fun filterResults(results: List<IPlatformContent>): List<IPlatformContent> {
            return results.filter {
                if(StateMeta.instance.isVideoHidden(it.url))
                    return@filter false;
                if(StateMeta.instance.isCreatorHidden(it.author.url))
                    return@filter false;

                if(fragment._toggleRecent && (it.datetime?.getNowDiffHours() ?: 0) > 25)
                    return@filter false;
                if(fragment._toggleWatched && StateHistory.instance.isHistoryWatched(it.url, 0))
                    return@filter false;
                if(fragment._togglePluginsDisabled.any() && it.id.pluginId != null && fragment._togglePluginsDisabled.contains(it.id.pluginId)) {
                    return@filter false;
                }

                return@filter true;
            };
        }

        private fun loadResults(withRefetch: Boolean = true) {
            setLoading(true);
            _taskGetPager.run(withRefetch);
        }
        private fun loadedResult(pager : IPager<IPlatformContent>) {
            if (pager is EmptyPager<IPlatformContent>) {
                //StateAnnouncement.instance.registerAnnouncement(UUID.randomUUID().toString(), context.getString(R.string.no_home_available), context.getString(R.string.no_home_page_is_available_please_check_if_you_are_connected_to_the_internet_and_refresh), AnnouncementType.SESSION);
            }

            Logger.i(TAG, "Got new home pager $pager");
            finishRefreshLayoutLoader();
            setLoading(false);
            setPager(pager);
            if(pager.getResults().isEmpty() && !pager.hasMorePages()) {
                setLoading(false);
                setEmptyPager(true);
            }
        }
    }

    companion object {
        const val TAG = "HomeFragment";

        fun newInstance() = HomeFragment().apply {}
    }
}