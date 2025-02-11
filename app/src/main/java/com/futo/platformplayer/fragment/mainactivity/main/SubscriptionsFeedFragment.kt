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
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.TaskHandler
import com.futo.platformplayer.engine.exceptions.PluginException
import com.futo.platformplayer.exceptions.ChannelException
import com.futo.platformplayer.exceptions.RateLimitException
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.SearchType
import com.futo.platformplayer.models.SubscriptionGroup
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateCache
import com.futo.platformplayer.states.StateHistory
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.FragmentedStorageFileJson
import com.futo.platformplayer.views.FeedStyle
import com.futo.platformplayer.views.NoResultsView
import com.futo.platformplayer.views.ToastView
import com.futo.platformplayer.views.adapters.ContentPreviewViewHolder
import com.futo.platformplayer.views.adapters.InsertedViewAdapterWithLoader
import com.futo.platformplayer.views.adapters.InsertedViewHolder
import com.futo.platformplayer.views.buttons.BigButton
import com.futo.platformplayer.views.subscriptions.SubscriptionBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import kotlin.system.measureTimeMillis

class SubscriptionsFeedFragment : MainFragment() {
    override val isMainView : Boolean = true;
    override val isTab: Boolean = true;
    override val hasBottomBar: Boolean get() = true;

    private var _view: SubscriptionsFeedView? = null;
    private var _group: SubscriptionGroup? = null;
    private var _cachedRecyclerData: FeedView.RecyclerData<InsertedViewAdapterWithLoader<ContentPreviewViewHolder>, GridLayoutManager, IPager<IPlatformContent>, IPlatformContent, IPlatformContent, InsertedViewHolder<ContentPreviewViewHolder>>? = null;

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
        val view = SubscriptionsFeedView(this, inflater, _cachedRecyclerData);
        _view = view;
        if(_group != null)
            view.selectSubgroup(_group);
        return view;
    }

    override fun onDestroyMainView() {
        super.onDestroyMainView();
        val view = _view;
        if (view != null) {
            _cachedRecyclerData = view.recyclerData;
            _group = view.subGroup;
            view.cleanup();
            _view = null;
        }
    }

    override fun onBackPressed(): Boolean {
        if (_view?.onBackPressed() == true)
            return true

        return super.onBackPressed()
    }

    fun setPreviewsEnabled(previewsEnabled: Boolean) {
        _view?.setPreviewsEnabled(previewsEnabled && Settings.instance.subscriptions.previewFeedItems);
    }

    @SuppressLint("ViewConstructor")
    class SubscriptionsFeedView : ContentFeedView<SubscriptionsFeedFragment> {
        override val shouldShowTimeBar: Boolean get() = Settings.instance.subscriptions.progressBar

        var subGroup: SubscriptionGroup? = null;

        constructor(fragment: SubscriptionsFeedFragment, inflater: LayoutInflater, cachedRecyclerData: RecyclerData<InsertedViewAdapterWithLoader<ContentPreviewViewHolder>, GridLayoutManager, IPager<IPlatformContent>, IPlatformContent, IPlatformContent, InsertedViewHolder<ContentPreviewViewHolder>>? = null) : super(fragment, inflater, cachedRecyclerData) {
            Logger.i(TAG, "SubscriptionsFeedFragment constructor()");
            StateSubscriptions.instance.global.onUpdateProgress.subscribe(this) { progress, total ->
            };

            StateSubscriptions.instance.onSubscriptionsChanged.subscribe(this) { _, added ->
                if(!added)
                    StateSubscriptions.instance.clearSubscriptionFeed();
                StateApp.instance.scopeOrNull?.let {
                    StateSubscriptions.instance.updateSubscriptionFeed(it);
                }
                recyclerData.lastLoad = OffsetDateTime.MIN;
            };

            initializeToolbarContent();

            setPreviewsEnabled(Settings.instance.subscriptions.previewFeedItems);
            if (Settings.instance.tabs.find { it.id == 0 }?.enabled != true) {
                showAnnouncementView()
            }
        }

        fun onShown() {
            Logger.i(TAG, "SubscriptionsFeedFragment onShown()");
            val currentProgress = StateSubscriptions.instance.getGlobalSubscriptionProgress();
            setProgress(currentProgress.first, currentProgress.second);

            if(recyclerData.loadedFeedStyle != feedStyle ||
                recyclerData.lastLoad.getNowDiffSeconds() > 60 ) {
                recyclerData.lastLoad = OffsetDateTime.now();

                if(StateSubscriptions.instance.getOldestUpdateTime().getNowDiffMinutes() > 5 && Settings.instance.subscriptions.fetchOnTabOpen) {
                    loadResults(false);
                }
                else if(recyclerData.results.size == 0) {
                    loadCache();
                    setLoading(false);
                }
            }

            if (!StateSubscriptions.instance.global.isGlobalUpdating) {
                finishRefreshLayoutLoader();
            }

            StateSubscriptions.instance.onFeedProgress.subscribe(this) { id, progress, total ->
                if(subGroup?.id == id)
                    fragment.lifecycleScope.launch(Dispatchers.Main) {
                        try {
                            setProgress(progress, total);
                        } catch (e: Throwable) {
                            Logger.e(TAG, "Failed to set progress", e);
                        }
                    }
            }
        }

        override fun cleanup() {
            super.cleanup()
            StateSubscriptions.instance.global.onUpdateProgress.remove(this);
            StateSubscriptions.instance.onSubscriptionsChanged.remove(this);
            StateSubscriptions.instance.onFeedProgress.remove(this);
        }

        override val feedStyle: FeedStyle get() = Settings.instance.subscriptions.getSubscriptionsFeedStyle();

        private var _subscriptionBar: SubscriptionBar? = null;

        @Serializable
        class FeedFilterSettings: FragmentedStorageFileJson() {
            val allowContentTypes: MutableList<ContentType> = mutableListOf(ContentType.MEDIA, ContentType.POST);
            var allowLive: Boolean = true;
            var allowPlanned: Boolean = false;
            var allowWatched: Boolean = true;
            override fun encode(): String {
                return Json.encodeToString(this);
            }
        }
        private val _filterLock = Object();
        private val _filterSettings = FragmentedStorage.get<FeedFilterSettings>("subFeedFilter");

        private var _bypassRateLimit = false;
        private val _lastExceptions: List<Throwable>? = null;
        private val _taskGetPager = TaskHandler<Boolean, IPager<IPlatformContent>>({StateApp.instance.scope}, { withRefresh ->
            val group = subGroup;
            if(!_bypassRateLimit) {
                val subRequestCounts = StateSubscriptions.instance.getSubscriptionRequestCount(group);
                val reqCountStr = subRequestCounts.map { "    ${it.key.config.name}: ${it.value}/${it.key.getSubscriptionRateLimit()}" }.joinToString("\n");
                val rateLimitPlugins = subRequestCounts.filter { clientCount -> clientCount.key.getSubscriptionRateLimit()?.let { rateLimit -> clientCount.value > rateLimit } == true }
                Logger.w(TAG, "Trying to refreshing subscriptions with requests:\n$reqCountStr");
                if(rateLimitPlugins.any())
                    throw RateLimitException(rateLimitPlugins.map { it.key.id });
            }
            _bypassRateLimit = false;
            val resp = StateSubscriptions.instance.getGlobalSubscriptionFeed(StateApp.instance.scope, withRefresh, group);
            val feed = StateSubscriptions.instance.getFeed(group?.id);

            val currentExs = feed?.exceptions ?: listOf();
            if(currentExs != _lastExceptions && currentExs.any()) {
                handleExceptions(currentExs)
                feed?.exceptions = listOf()
            }

            return@TaskHandler resp;
        })
            .success {
                if(!Settings.instance.subscriptions.alwaysReloadFromCache)
                    loadedResult(it);
                else {
                    finishRefreshLayoutLoader();
                    setLoading(false);
                    loadCache();
                }
            } //TODO: Remove
            .exception<RateLimitException> {
                fragment.lifecycleScope.launch(Dispatchers.IO) {
                    val subs = StateSubscriptions.instance.getSubscriptions();
                    val subsByLimited = it.pluginIds.map{ StatePlatform.instance.getClientOrNull(it) }
                        .filterIsInstance<JSClient>()
                        .associateWith { client -> subs.filter { it.getClient() == client } }
                        .map { Pair(it.key, it.value) }

                    withContext(Dispatchers.Main) {
                        UIDialogs.showDialog(context, R.drawable.ic_security_pred,
                            context.getString(R.string.rate_limit_warning),  context.getString(R.string.this_is_a_temporary_measure_to_prevent_people_from_hitting_rate_limit_until_we_have_better_support_for_lots_of_subscriptions) + context.getString(R.string.you_have_too_many_subscriptions_for_the_following_plugins),
                            subsByLimited.map { it.first.config.name + ": " + it.second.size + " " + context.getString(R.string.subscriptions) } .joinToString("\n"), 0, UIDialogs.Action("Refresh Anyway", {
                                _bypassRateLimit = true;
                                loadResults(true);
                            }, UIDialogs.ActionStyle.DANGEROUS_TEXT),
                            UIDialogs.Action("OK", {
                                finishRefreshLayoutLoader();
                                setLoading(false);
                            }, UIDialogs.ActionStyle.PRIMARY));
                    }
                }
            }
            .exception<Throwable> {
                Logger.w(ChannelFragment.TAG, "Failed to load channel.", it);
                if(it !is CancellationException)
                    UIDialogs.showGeneralRetryErrorDialog(context, it.message ?: "", it, { loadResults(true) }, null, fragment);
                else {
                    finishRefreshLayoutLoader();
                    setLoading(false);
                }
            };

        fun selectSubgroup(g: SubscriptionGroup?) {
            if(g != null)
                _subscriptionBar?.selectGroup(g);
        }

        private fun initializeToolbarContent() {
            _subscriptionBar = SubscriptionBar(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            };
            _subscriptionBar?.onClickChannel?.subscribe { c -> fragment.navigate<ChannelFragment>(c); };
            _subscriptionBar?.onToggleGroup?.subscribe { g ->
                if(g is SubscriptionGroup.Add)
                    UISlideOverlays.showCreateSubscriptionGroup(_overlayContainer);
                else {
                    subGroup = g;
                    setProgress(0, 0);
                    if(Settings.instance.subscriptions.fetchOnTabOpen) {
                        loadCache();
                        loadResults(false);
                    }
                    else if(g != null && StateSubscriptions.instance.getFeed(g.id) != null) {
                        loadResults(false);
                    }
                    else
                        loadCache();
                }
            };
            _subscriptionBar?.onHoldGroup?.subscribe { g ->
                if(g !is SubscriptionGroup.Add)
                    fragment.navigate<SubscriptionGroupFragment>(g);
            };

            synchronized(_filterLock) {
                _subscriptionBar?.setToggles(
                    SubscriptionBar.Toggle(context.getString(R.string.videos), _filterSettings.allowContentTypes.contains(ContentType.MEDIA)) { toggleFilterContentTypes(listOf(ContentType.MEDIA, ContentType.NESTED_VIDEO), it);  },
                    SubscriptionBar.Toggle(context.getString(R.string.posts),  _filterSettings.allowContentTypes.contains(ContentType.POST)) { toggleFilterContentType(ContentType.POST, it); },
                    SubscriptionBar.Toggle(context.getString(R.string.live), _filterSettings.allowLive) { _filterSettings.allowLive = it; _filterSettings.save(); loadResults(false); },
                    SubscriptionBar.Toggle(context.getString(R.string.planned), _filterSettings.allowPlanned) { _filterSettings.allowPlanned = it; _filterSettings.save(); loadResults(false); },
                    SubscriptionBar.Toggle(context.getString(R.string.watched), _filterSettings.allowWatched) { _filterSettings.allowWatched = it; _filterSettings.save(); loadResults(false); }
                );
            }

            _toolbarContentView.addView(_subscriptionBar, 0);
        }
        private fun toggleFilterContentTypes(contentTypes: List<ContentType>, isTrue: Boolean) {
            for(contentType in contentTypes)
                toggleFilterContentType(contentType, isTrue);
        }
        private fun toggleFilterContentType(contentType: ContentType, isTrue: Boolean) {
            synchronized(_filterLock) {
                if(!isTrue) {
                    _filterSettings.allowContentTypes.remove(contentType);
                } else if(!_filterSettings.allowContentTypes.contains(contentType)) {
                    _filterSettings.allowContentTypes.add(contentType)
                }
                _filterSettings.save();
            };
            if(Settings.instance.subscriptions.fetchOnTabOpen) { //TODO: Do this different, temporary workaround
                loadResults(false);
            } else {
                loadCache();
            }
        }

        override fun filterResults(results: List<IPlatformContent>): List<IPlatformContent> {
            val nowSoon = OffsetDateTime.now().plusMinutes(5);
            val filterGroup = subGroup;
            return results.filter {
                val allowedContentType = _filterSettings.allowContentTypes.contains(if(it.contentType == ContentType.NESTED_VIDEO || it.contentType == ContentType.LOCKED) ContentType.MEDIA else it.contentType);

                if(it is IPlatformVideo && it.duration > 0 && !_filterSettings.allowWatched && StateHistory.instance.isHistoryWatched(it.url, it.duration))
                    return@filter false;

                //TODO: Check against a sub cache
                if(filterGroup != null && !filterGroup.urls.contains(it.author.url))
                    return@filter false;


                if(it.datetime?.isAfter(nowSoon) == true) {
                    if(!_filterSettings.allowPlanned)
                        return@filter false;
                }

                if(_filterSettings.allowLive) { //If allowLive, always show live
                    if(it is IPlatformVideo && it.isLive)
                        return@filter true;
                }
                else if(it is IPlatformVideo && it.isLive)
                    return@filter false;

                return@filter allowedContentType;
            };
        }

        override fun reload() {
            StatePlugins.instance.clearUpdating(); //Fallback in case it doesnt clear, UI should be blocked.
            loadResults(true);
        }


        private fun loadCache() {
            fragment.lifecycleScope.launch(Dispatchers.IO) {
                val cachePager: IPager<IPlatformContent>;
                Logger.i(TAG, "Subscriptions retrieving cache");
                val time = measureTimeMillis {
                    cachePager = StateCache.instance.getSubscriptionCachePager();
                }
                Logger.i(TAG, "Subscriptions retrieved cache (${time}ms)");

                withContext(Dispatchers.Main) {
                    val results = cachePager.getResults();
                    Logger.i(TAG, "Subscriptions show cache (${results.size})");
                    setEmptyPager(results.isEmpty());
                    setPager(cachePager);
                }
            }
        }
        private fun loadResults(withRefetch: Boolean = false) {
            setLoading(true);
            Logger.i(TAG, "Subscriptions load");
            if(recyclerData.results.size == 0) {
                loadCache();
            } else
                setEmptyPager(false);
            _taskGetPager.run(withRefetch);
        }

        override fun onRestoreCachedData(cachedData: RecyclerData<InsertedViewAdapterWithLoader<ContentPreviewViewHolder>, GridLayoutManager, IPager<IPlatformContent>, IPlatformContent, IPlatformContent, InsertedViewHolder<ContentPreviewViewHolder>>) {
            super.onRestoreCachedData(cachedData);
            setEmptyPager(cachedData.results.isEmpty());
        }
        private fun loadedResult(pager: IPager<IPlatformContent>) {
            Logger.i(TAG, "Subscriptions new pager loaded (${pager.getResults().size})");

            fragment.lifecycleScope.launch(Dispatchers.Main) {
                try {
                    finishRefreshLayoutLoader();
                    setLoading(false);
                    setPager(pager);
                    setEmptyPager(pager.getResults().isEmpty());
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to finish loading", e)
                }
            }/*.invokeOnCompletion { //Commented for now, because it doesn't fix the bug it was intended to fix, but might want it later anyway
                if(it is CancellationException) {
                    setLoading(false);
                }
            }*/
        }
        override fun getEmptyPagerView(): View? {
            val dp10 = 10.dp(resources);
            val dp30 = 30.dp(resources);
            if(StateSubscriptions.instance.getSubscriptions().isEmpty())
                return NoResultsView(context, "You have no subscriptions", "Subscribe to some creators or import them from elsewhere.", R.drawable.ic_explore, listOf(
                    BigButton(context, "Search", "Search for creators in your enabled plugins", R.drawable.ic_creators) {
                        fragment.navigate<SuggestionsFragment>(SuggestionsFragmentData("", SearchType.CREATOR));
                    }.withMargin(dp10, dp30),
                    BigButton(context, "Import", "Import your subscriptions from another format", R.drawable.ic_move_up) {
                        val activity = StateApp.instance.context;
                        if(activity is MainActivity)
                            UIDialogs.showImportOptionsDialog(activity);
                    }.withMargin(dp10, dp30)
                ));
            return null;
        }

        private fun handleExceptions(exs: List<Throwable>) {
            context?.let {
                fragment.lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        if (exs.size <= 3) {
                            for (ex in exs) {
                                var toShow = ex;
                                var channel: String? = null;
                                if (toShow is ChannelException) {
                                    channel = toShow.channelNameOrUrl;
                                    toShow = toShow.cause!!;
                                }
                                Logger.e(TAG, "Channel [${channel}] failed", ex);
                                if (toShow is PluginException)
                                    UIDialogs.appToast(ToastView.Toast(
                                                toShow.message +
                                                (if(channel != null) "\nChannel: $channel" else ""), false, null,
                                        "Plugin ${toShow.config.name} failed")
                                    );
                                else
                                    UIDialogs.appToast(ex.message ?: "");
                            }
                        }
                        else {
                            val failedChannels = exs.filterIsInstance<ChannelException>().map { it.channelNameOrUrl }.distinct().toList();
                            val failedPlugins = exs.filter { it is PluginException || (it is ChannelException && it.cause is PluginException) }
                                .map { if(it is ChannelException) (it.cause as PluginException) else if(it is PluginException) it else null  }
                                .filterNotNull()
                                .distinctBy { it?.config?.name }
                                .map { it!! }
                                .toList();
                            for(distinctPluginFail in failedPlugins)
                                UIDialogs.appToast(context.getString(R.string.plugin_pluginname_failed_message).replace("{pluginName}", distinctPluginFail.config.name).replace("{message}", distinctPluginFail.message ?: ""));
                            if(failedChannels.isNotEmpty())
                                UIDialogs.appToast(ToastView.Toast(failedChannels.take(3).map { "- $it" }.joinToString("\n") +
                                        (if(failedChannels.size >= 3) "\nAnd ${failedChannels.size - 3} more" else ""), false, null, "Failed Channels"));
                        }
                    } catch (e: Throwable) {
                        Logger.e(TAG, "Failed to handle exceptions", e)
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "SubscriptionsFeedFragment";

        fun newInstance() = SubscriptionsFeedFragment().apply {}
    }
}