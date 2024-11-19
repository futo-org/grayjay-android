package com.futo.platformplayer.states

import android.content.Context
import androidx.collection.LruCache
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.IPluginSourced
import com.futo.platformplayer.api.media.PlatformMultiClientPool
import com.futo.platformplayer.api.media.exceptions.NoPlatformClientException
import com.futo.platformplayer.api.media.models.FilterGroup
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.chapters.IChapter
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.contents.PlatformContentPlaceholder
import com.futo.platformplayer.api.media.models.live.ILiveChatWindowDescriptor
import com.futo.platformplayer.api.media.models.live.IPlatformLiveEvent
import com.futo.platformplayer.api.media.models.playback.IPlaybackTracker
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylistDetails
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.platforms.js.DevJSClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.structures.EmptyPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.api.media.structures.MultiChronoContentPager
import com.futo.platformplayer.api.media.structures.MultiDistributionChannelPager
import com.futo.platformplayer.api.media.structures.MultiDistributionContentPager
import com.futo.platformplayer.api.media.structures.PlaceholderPager
import com.futo.platformplayer.api.media.structures.RefreshDistributionContentPager
import com.futo.platformplayer.awaitFirstNotNullDeferred
import com.futo.platformplayer.constructs.BatchedTaskHandler
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.fromPool
import com.futo.platformplayer.getNowDiffDays
import com.futo.platformplayer.getNowDiffSeconds
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.ImageVariable
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringArrayStorage
import com.futo.platformplayer.stores.StringStorage
import com.futo.platformplayer.views.ToastView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.internal.concat
import java.lang.Thread.sleep
import java.time.OffsetDateTime
import kotlin.streams.asSequence

/***
 * Used to interact with sources/clients
 */
class StatePlatform {
    private val TAG = "StatePlatform";
    private val VIDEO_CACHE = 100;

    private val _scope = CoroutineScope(Dispatchers.IO);

    //Caches
    private data class CachedPlatformContent(val video: IPlatformContentDetails, val creationTime: OffsetDateTime = OffsetDateTime.now());
    private val _cacheExpirationSeconds = 60 * 5;
    private val _cache : LruCache<String, CachedPlatformContent> = LruCache<String, CachedPlatformContent>(VIDEO_CACHE);

    //Clients
    private val _enabledClientsPersistent = FragmentedStorage.get<StringArrayStorage>("enabledClients");
    private val _platformOrderPersistent = FragmentedStorage.get<StringArrayStorage>("platformOrder");
    private val _clientsLock = Object();
    private val _availableClients : ArrayList<IPlatformClient> = ArrayList();
    private val _enabledClients : ArrayList<IPlatformClient> = ArrayList();

    //ClientPools are used to isolate plugin usage of certain components from others
    //This prevents for example a background task like subscriptions from blocking a user from opening a video
    //It also allows parallel usage of plugins that would otherwise be impossible.
    //Pools always follow the behavior of the base client. So if user disables a plugin, it kills all pooled clients.
    //Each pooled client adds additional memory usage.
    //WARNING: Be careful with pooling some calls, as they might use the plugin subsequently afterwards. For example pagers might block plugins in future calls.
    private val _mainClientPool = PlatformMultiClientPool("Main", 2); //Used for all main user events, generally user critical
    private val _pagerClientPool = PlatformMultiClientPool("Pagers", 2); //Used primarily for calls that result in front-end pagers, preventing them from blocking other calls.
    private val _channelClientPool = PlatformMultiClientPool("Channels", 15); //Used primarily for subscription/background channel fetches
    private val _trackerClientPool = PlatformMultiClientPool("Trackers", 1); //Used exclusively for playback trackers
    private val _liveEventClientPool = PlatformMultiClientPool("LiveEvents", 1); //Used exclusively for live events
    private val _privateClientPool = PlatformMultiClientPool("Private", 2, true); //Used primarily for calls if in incognito mode


    private val _icons : HashMap<String, ImageVariable> = HashMap();

    val hasClients: Boolean get() = _availableClients.size > 0;

    val onSourceDisabled = Event1<IPlatformClient>();

    val onDevSourceChanged = Event0();

    //TODO: Remove after verifying that enabled clients are already in persistent order
    val platformOrder get() = _platformOrderPersistent.values.toList();

    //Batched Requests
    private val _batchTaskGetVideoDetails: BatchedTaskHandler<String, IPlatformContentDetails> = BatchedTaskHandler<String, IPlatformContentDetails>(_scope,
        { url ->

            Logger.i(StatePlatform::class.java.name, "Fetching video details [${url}]");
            if(!StateApp.instance.privateMode) {
                _enabledClients.find { it.isContentDetailsUrl(url) }?.let {
                    _mainClientPool.getClientPooled(it).getContentDetails(url)
                }
                    ?: throw NoPlatformClientException("No client enabled that supports this url ($url)");
            }
            else {
                Logger.i(TAG, "Fetching details with private client");
                _enabledClients.find { it.isContentDetailsUrl(url) }?.let {
                    _privateClientPool.getClientPooled(it).getContentDetails(url)
                }
                    ?: throw NoPlatformClientException("No client enabled that supports this url ($url)");
            }
        },
        {
            if(!Settings.instance.browsing.videoCache || StateApp.instance.privateMode)
                return@BatchedTaskHandler null;
            else {
                val cached = synchronized(_cache) { _cache.get(it); } ?: return@BatchedTaskHandler null;
                Logger.i(TAG, "Video Cache Hit [${cached.video.name}]");
                if (cached.creationTime.getNowDiffSeconds() > _cacheExpirationSeconds) {
                    Logger.i(TAG, "Invalidated cache for [${it}]");
                    synchronized(_cache) {
                        _cache.remove(it);
                    }
                    return@BatchedTaskHandler null;
                }
                return@BatchedTaskHandler cached.video;
            }
        },
        { para, result ->
            if(!Settings.instance.browsing.videoCache || (result is IPlatformVideo && result.isLive) || StateApp.instance.privateMode)
                return@BatchedTaskHandler
            else {
                Logger.i(TAG, "Caching [${para}]");
                if (result.datetime == null || result.datetime!! < OffsetDateTime.now())
                    synchronized(_cache) {
                        _cache.put(para, CachedPlatformContent(result))
                    }
            }
        });

    constructor() {
        onSourceDisabled.subscribe {
            synchronized(_cache) {
                for(item in _cache.snapshot()) {
                    if(item.value.video is IPluginSourced)
                        if(it.id == (item.value.video as IPluginSourced).sourceConfig.id) {
                            Logger.i(TAG, "Removing [${item.value.video.name}] from cache because plugin disabled");
                            _cache.remove(item.key);

                        }
                }
            }
        };
    }


    suspend fun updateAvailableClients(context: Context, reloadPlugins: Boolean = false) {
        if(reloadPlugins) {
            StatePlugins.instance.reloadPluginFile();
        }

        withContext(Dispatchers.IO) {
            var enabled: Array<String>;
            synchronized(_clientsLock) {
                for(e in _enabledClients) {
                    try {
                        e.disable();
                        onSourceDisabled.emit(e);
                    }
                    catch(ex: Throwable) {
                        UIDialogs.appToast(ToastView.Toast("If this happens often, please inform the developers on Github", false, null, "Plugin [${e.name}] failed to disable"));
                    }
                }

                _enabledClients.clear();
                _availableClients.clear();

                _icons.clear();
                _icons[StateDeveloper.DEV_ID] = ImageVariable(null, R.drawable.ic_security_red);

                StatePlugins.instance.updateEmbeddedPlugins(context);
                StatePlugins.instance.installMissingEmbeddedPlugins(context);

                for (plugin in StatePlugins.instance.getPlugins()) {
                    _icons[plugin.config.id] = StatePlugins.instance.getPluginIconOrNull(plugin.config.id) ?:
                            ImageVariable(plugin.config.absoluteIconUrl, null);

                    val client = JSClient(context, plugin);
                    client.onCaptchaException.subscribe { c, ex ->
                        StateApp.instance.handleCaptchaException(c, ex);
                    }
                    _availableClients.add(client);
                }

                if(_availableClients.distinctBy { it.id }.count() < _availableClients.size) {
                    val dups = _availableClients.filter { x-> _availableClients.count { it.id == x.id } > 1 };
                    val overrideClients = _availableClients.distinctBy { it.id }
                    _availableClients.clear();
                    _availableClients.addAll(overrideClients);

                    StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                        UIDialogs.showDialog(context, R.drawable.ic_error_pred, "Duplicate plugin ids detected", "This can cause unexpected behavior, ideally uninstall duplicate plugins (ids)",
                            dups.map { it.name }.joinToString("\n"), 0, UIDialogs.Action("Ok", { }));
                    }

                    //throw IllegalStateException("Attempted to add 2 clients with the same ID");
                }

                enabled = _enabledClientsPersistent.getAllValues()
                    .filter { _availableClients.any { ac -> ac.id == it } }
                    .toTypedArray();
                if(enabled.isEmpty()) {
                    enabled = StatePlugins.instance.getEmbeddedSourcesDefault(context)
                        .filter { id -> _availableClients.any { it.id == id } }
                        .toTypedArray();
                }
            }
            selectClients(*enabled);
        };
    }

    fun isClientEnabled(id: String): Boolean {
        synchronized(_clientsLock) {
            return _enabledClients.any { it.id == id };
        }
    }
    fun isClientEnabled(client: IPlatformClient): Boolean {
        synchronized (_clientsLock) {
            return _enabledClients.contains(client);
        }
    }

    fun getAvailableClients(): List<IPlatformClient> {
        synchronized(_clientsLock) {
            return _availableClients.toList();
        }
    }
    fun getEnabledClients(): List<IPlatformClient> {
        synchronized(_clientsLock) {
            return _enabledClients.toList();
        }
    }

    //TODO: getEnabledClients should already be ordered, remove after verify that that is the case
    fun getSortedEnabledClient(): List<IPlatformClient> {
        synchronized(_clientsLock) {
            val enabledClients = _enabledClients;
            val orderedSources = platformOrder.mapNotNull { order ->
                enabledClients.firstOrNull { it.name == order }
            }

            val remainingSources = enabledClients.filter { it !in orderedSources }
            return orderedSources + remainingSources;
        }
    }
    fun getClientOrNullByUrl(url: String): IPlatformClient? {
        return getChannelClientOrNull(url) ?: getPlaylistClientOrNull(url) ?: getContentClientOrNull(url);
    }
    fun getClientOrNull(id: String): IPlatformClient? {
        synchronized(_clientsLock) {
            return _availableClients.find { it.id == id };
        }
    }
    fun getClient(id: String): IPlatformClient {
        return getClientOrNull(id) ?: throw IllegalArgumentException("Client with id $id does not exist");
    }

    fun getClientsByClaimType(claimType: Int): List<IPlatformClient> {
        return getEnabledClients().filter { it.isClaimTypeSupported(claimType) };
    }
    fun getClientByClaimTypeOrNull(claimType: Int): IPlatformClient? {
        return getEnabledClients().firstOrNull { it.isClaimTypeSupported(claimType) };
    }
    fun getDevClient() : DevJSClient? {
        return getClientOrNull(StateDeveloper.DEV_ID) as DevJSClient?;
    }

    fun getPlatformIcon(type: String?) : ImageVariable? {
        if(type == null)
            return null;
        if(_icons.containsKey(type))
            return _icons[type];
        return null;
    }

    fun setPlatformOrder(platformOrder: List<String>) {
        _platformOrderPersistent.values.clear();
        _platformOrderPersistent.values.addAll(platformOrder);
        _platformOrderPersistent.save();
    }

    suspend fun reloadClient(context: Context, id: String) : JSClient? {
        return withContext(Dispatchers.IO) {
            val client = getClient(id);
            if (client !is JSClient)
                return@withContext null; //TODO: Error?

            Logger.i(TAG, "Reloading plugin ${client.name}");

            val newClient = if (client is DevJSClient)
                client.recreate(context);
            else
                JSClient(context,
                    StatePlugins.instance.getPlugin(id)
                        ?: throw IllegalStateException("Client existed, but plugin config didn't")
                );
            newClient.onCaptchaException.subscribe { c, ex ->
                StateApp.instance.handleCaptchaException(c, ex);
            }

            synchronized(_clientsLock) {
                if (_enabledClients.contains(client)) {
                    _enabledClients.remove(client);
                    client.disable();
                    onSourceDisabled.emit(client);
                    newClient.initialize();
                    _enabledClients.add(newClient);
                }

                _availableClients.removeIf { it.id == id };
                _availableClients.add(newClient);
            }
            return@withContext newClient;
        };
    }


    suspend fun enableClient(ids: List<String>) {
        val currentClients = getEnabledClients().map { it.id };
        selectClients(*(currentClients + ids).distinct().toTypedArray());
    }
    /**
     * Selects the enabled clients, meaning all clients that data is actively requested from.
     * If a client is disabled, NO requests are made to said client
     */
    suspend fun selectClients(vararg ids: String) {
        withContext(Dispatchers.IO) {
            synchronized(_clientsLock) {
                val removed = _enabledClients.toMutableList();
                _enabledClients.clear();
                for (id in ids) {
                    val client = getClient(id);
                    try {
                        if (!removed.removeIf { it == client })
                            client.initialize();
                        _enabledClients.add(client);
                    }
                    catch(ex: Exception) {
                        Logger.e(TAG, "Plugin ${client.name} failed to load\n${ex.message}", ex)
                        UIDialogs.toast("Plugin ${client.name} failed to load\n${ex.message}");
                    }
                }
                _enabledClientsPersistent.set(*ids);
                _enabledClientsPersistent.save();

                for (oldClient in removed) {
                    oldClient.disable();
                    onSourceDisabled.emit(oldClient);
                }
            }
        };
    }

    fun getHome(): IPager<IPlatformContent> {
        Logger.i(TAG, "Platform - getHome");
        var clientIdsOngoing = mutableListOf<String>();
        val clients = getSortedEnabledClient().filter { if (it is JSClient) it.enableInHome else true };

        StateApp.instance.scopeOrNull?.let {
            it.launch(Dispatchers.Default) {
                try {
                    delay(5000);
                    val slowClients = synchronized(clientIdsOngoing) {
                        return@synchronized clients.filter { clientIdsOngoing.contains(it.id) };
                    };
                    for(client in slowClients)
                        UIDialogs.toast("${client.name} is still loading..\nConsider disabling it for Home", false);
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to show toast for slow source.", e)
                }
            }
        };

        val pages = clients.parallelStream()
            .map {
                Logger.i(TAG, "getHome - ${it.name}")
                synchronized(clientIdsOngoing) {
                    clientIdsOngoing.add(it.id);
                }
                val homeResult = it.fromPool(_pagerClientPool).getHome();
                synchronized(clientIdsOngoing) {
                    clientIdsOngoing.remove(it.id);
                }
                return@map homeResult;
            }
            .asSequence()
            .toList()
            .associateWith { 1f };

        val pager = MultiDistributionContentPager(pages);
        pager.initialize();
        return pager;
    }
    suspend fun getHomeRefresh(scope: CoroutineScope): IPager<IPlatformContent> {
        Logger.i(TAG, "Platform - getHome (Refresh)");
        val clients = getSortedEnabledClient().filter { if (it is JSClient) it.enableInHome else true };

        val deferred: List<Pair<IPlatformClient, Deferred<IPager<IPlatformContent>?>>> = clients.map {
            return@map Pair(it, scope.async(Dispatchers.IO) {
                try {
                    var searchResult = it.fromPool(_pagerClientPool).getHome();
                    if(searchResult.getResults().size == 0) {
                        Logger.i(TAG, "No home results, retrying");
                        sleep(500);
                        searchResult = it.fromPool(_pagerClientPool).getHome();
                    }
                    return@async searchResult;
                } catch(ex: Throwable) {
                    Logger.e(TAG, "getHomeRefresh", ex);
                    //throw ex;
                    //return@async null;
                    return@async PlaceholderPager(10, { PlatformContentPlaceholder(it.id, ex) });
                }
            });
        }.toList();

        val finishedPager = deferred.map { it.second }.awaitFirstNotNullDeferred() ?: return EmptyPager();
        val toAwait = deferred.filter { it.second != finishedPager.first };

        return RefreshDistributionContentPager(
            listOf(finishedPager.second),
            toAwait.map { it.second },
            toAwait.map { PlaceholderPager(5, { PlatformContentPlaceholder(it.first.id) }) });
    }


    //Search
    fun searchSuggestions(query: String): Array<String> {
        Logger.i(TAG, "Platform - searchSuggestions");
        //TODO: hasSearchSuggestions
        return getEnabledClients().firstOrNull()?.searchSuggestions(query) ?: arrayOf();
    }

    fun search(query: String, type: String? = null, sort: String? = null, filters: Map<String, List<String>> = mapOf(), clientIds: List<String>? = null): IPager<IPlatformContent> {
        Logger.i(TAG, "Platform - search");
        val pagers = mutableMapOf<IPager<IPlatformContent>, Float>();
        val clients = if (clientIds != null) getSortedEnabledClient().filter { (if (it is JSClient) it.enableInSearch else true) && clientIds.contains(it.id) }
            else getSortedEnabledClient().filter { if (it is JSClient) it.enableInSearch else true };

        for (c in clients) {
            Logger.i(TAG, "Client enabled for search: " + c.name)
        }

        clients.parallelStream().forEach {
            val searchCapabilities = it.getSearchCapabilities();
            val mappedFilters = filters.map { pair -> Pair(pair.key, pair.value.map { v -> searchCapabilities.filters.first { g -> g.idOrName == pair.key }.filters.first { f -> f.idOrName == v }.value }) }.toMap();
            pagers.put(it.search(query, type, sort, mappedFilters), 1f);
        };

        val pager = MultiDistributionContentPager(pagers);
        pager.initialize();
        return pager;
    }
    fun searchPlaylist(query: String, type: String? = null, sort: String? = null, filters: Map<String, List<String>> = mapOf(), clientIds: List<String>? = null): IPager<IPlatformContent> {
        Logger.i(TAG, "Platform - search playlist");
        val pagers = mutableMapOf<IPager<IPlatformContent>, Float>();
        val clients = if (clientIds != null) getSortedEnabledClient().filter { (if (it is JSClient) it.enableInSearch else true) && clientIds.contains(it.id) }
        else getSortedEnabledClient().filter { if (it is JSClient) it.enableInSearch else true };

        for (c in clients) {
            Logger.i(TAG, "Client enabled for search: " + c.name)
        }

        clients.filter { it.capabilities.hasSearchPlaylists }.parallelStream().forEach {
            val searchCapabilities = it.getSearchCapabilities();
            val mappedFilters = filters.map { pair -> Pair(pair.key, pair.value.map { v -> searchCapabilities.filters.first { g -> g.idOrName == pair.key }.filters.first { f -> f.idOrName == v }.value }) }.toMap();
            pagers.put(it.searchPlaylists(query, type, sort, mappedFilters), 1f);
        };

        val pager = MultiDistributionContentPager(pagers);
        pager.initialize();
        return pager;
    }
    suspend fun searchRefresh(scope: CoroutineScope, query: String, type: String? = null, sort: String? = null, filters: Map<String, List<String>> = mapOf(), clientIds: List<String>? = null): IPager<IPlatformContent> {
        Logger.i(TAG, "Platform - search (refresh)");
        val clients =
            if (clientIds != null) getSortedEnabledClient().filter { (if (it is JSClient) it.enableInSearch else true) && clientIds.contains(it.id) }
            else getSortedEnabledClient().filter { if (it is JSClient) it.enableInSearch else true };

        for (c in clients) {
            Logger.i(TAG, "Client enabled for search: " + c.name)
        }

        val deferred: List<Pair<IPlatformClient, Deferred<IPager<IPlatformContent>?>>> = clients.map {
            return@map Pair(it, scope.async(Dispatchers.IO) {
                try {
                    val searchCapabilities = it.getSearchCapabilities();
                    val mappedFilters = filters.map { pair -> Pair(pair.key, pair.value.map { v -> searchCapabilities.filters.first { g -> g.idOrName == pair.key }.filters.first { f -> f.idOrName == v }.value }) }.toMap();
                    val searchResult = it.search(query, type, sort, mappedFilters);
                    return@async searchResult;
                } catch(ex: Throwable) {
                    Logger.e(TAG, "searchRefresh", ex);
                    return@async null;
                }
            });
        }.toList();

        val finishedPager = deferred.map { it.second }.awaitFirstNotNullDeferred() ?: return EmptyPager();
        val toAwait = deferred.filter { it.second != finishedPager.first };
        return RefreshDistributionContentPager(
            listOf(finishedPager.second),
            toAwait.map { it.second },
            toAwait.map { PlaceholderPager(5, { PlatformContentPlaceholder(it.first.id) }) });
    }

    fun searchChannel(channelUrl: String, query: String, type: String? = null, sort: String? = null, filters: Map<String, List<String>> = mapOf(), clientIds: List<String>? = null): IPager<IPlatformContent> {
        Logger.i(TAG, "Platform - search channel $channelUrl");

        val pagers = mutableMapOf<IPager<IPlatformContent>, Float>();
        val clients = if (clientIds != null) getSortedEnabledClient().filter { (if (it is JSClient) it.enableInSearch else true) && clientIds.contains(it.id) }
            else getSortedEnabledClient().filter { if (it is JSClient) it.enableInSearch else true };

        clients.parallelStream().forEach {
            val searchCapabilities = it.getSearchChannelContentsCapabilities();
            val mappedFilters = filters.map { pair -> Pair(pair.key, pair.value.map { v -> searchCapabilities.filters.first { g -> g.idOrName == pair.key }.filters.first { f -> f.idOrName == v }.value }) }.toMap();

            if (it.isChannelUrl(channelUrl)) {
                pagers.put(it.searchChannelContents(channelUrl, query, type, sort, mappedFilters), 1f);
            }
        };

        val pager = MultiDistributionContentPager(pagers);
        pager.initialize();
        return pager;
    }

    fun getCommonSearchCapabilities(clientIds: List<String>): ResultCapabilities? {
        return getCommonSearchCapabilitiesType(clientIds){
            it.getSearchCapabilities()
        };
    }
    fun getCommonSearchChannelContentsCapabilities(clientIds: List<String>): ResultCapabilities? {
        return getCommonSearchCapabilitiesType(clientIds){
            it.getSearchChannelContentsCapabilities()
        };
    }

    fun getCommonSearchCapabilitiesType(clientIds: List<String>, capabilitiesGetter: (client: IPlatformClient)-> ResultCapabilities): ResultCapabilities? {
        try {
            Logger.i(TAG, "Platform - getCommonSearchCapabilities");

            val clients = getEnabledClients().filter { clientIds.contains(it.id) };
            val c = clients.firstOrNull() ?: return null;
            val cap = capabilitiesGetter(c)//c.getSearchCapabilities();

            //var types = arrayListOf<String>();
            var sorts = cap.sorts.toMutableList();
            var filters = cap.filters.toMutableList();

            val sortsToRemove = arrayListOf<Int>();
            val filtersToRemove = arrayListOf<Int>();

            for (i in 1 until clients.size) {
                val clientSearchCapabilities = capabilitiesGetter(clients[i]);//.getSearchCapabilities();

                for (j in 0 until sorts.size) {
                    if (!clientSearchCapabilities.sorts.contains(sorts[j])) {
                        sortsToRemove.add(j);
                    }
                }

                sorts = sorts.filterIndexed { index, _ -> index !in sortsToRemove }.toMutableList();

                for (k in 0 until filters.size) {
                    val matchingFilterGroup = clientSearchCapabilities.filters.firstOrNull { f -> filters[k].idOrName == f.idOrName };
                    if (matchingFilterGroup == null) {
                        filtersToRemove.add(k);
                    } else {
                        val currentFilterGroup = filters[k];
                        filters[k] = FilterGroup(currentFilterGroup.name, currentFilterGroup.filters.filter { a -> matchingFilterGroup.filters.any { b -> a.idOrName == b.idOrName } }
                            , currentFilterGroup.isMultiSelect, currentFilterGroup.id);
                    }
                }

                filters = filters.filterIndexed { index, _ -> index !in filtersToRemove }.toMutableList();
            }

            return ResultCapabilities(listOf(), sorts, filters);
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to get common search capabilities.", e);
            return null;
        }
    }

    fun isSearchChannelsAvailable(): Boolean {
        return getEnabledClients().any { it.capabilities.hasChannelSearch };
    }
    fun searchChannels(query: String): IPager<PlatformAuthorLink> {
        Logger.i(TAG, "Platform - searchChannels");
        val pagers = mutableMapOf<IPager<PlatformAuthorLink>, Float>();
        getSortedEnabledClient().parallelStream().forEach {
            try {
                if (it.capabilities.hasChannelSearch)
                    pagers.put(it.searchChannels(query), 1f);
            }
            catch(ex: Throwable) {
                Logger.e(TAG, "Failed search channels", ex)
                UIDialogs.toast("Failed search channels on [${it.name}]\n(${ex.message})");
            }
        };
        if(pagers.isEmpty())
            return EmptyPager<PlatformAuthorLink>();

        val pager = MultiDistributionChannelPager(pagers);
        pager.initialize();
        return pager;
    }


    //Video
    fun hasEnabledVideoClient(url: String) : Boolean = getEnabledClients().any { it.isContentDetailsUrl(url) };
    fun getContentClient(url: String) : IPlatformClient = getContentClientOrNull(url)
        ?: throw NoPlatformClientException("No client enabled that supports this content url (${url})");
    fun getContentClientOrNull(url: String) : IPlatformClient? = getEnabledClients().find { it.isContentDetailsUrl(url) };
    fun getContentDetails(url: String, forceRefetch: Boolean = false): Deferred<IPlatformContentDetails> {
        Logger.i(TAG, "Platform - getContentDetails (${url})");
        if(forceRefetch)
            clearContentDetailCache(url);

        return _batchTaskGetVideoDetails.execute(url);
    }
    fun clearContentDetailCache(url: String) {
        if(_cache.get(url) != null) {
            Logger.i(TAG, "Force clearing cache (${url})");
            _cache.remove(url);
        }
    }

    fun getContentChapters(url: String): List<IChapter>? {
        val baseClient = getContentClientOrNull(url) ?: return null;
        if (baseClient !is JSClient) {
            return baseClient.getContentChapters(url);
        }
        val client = _trackerClientPool.getClientPooled(baseClient, 1);
        return client.getContentChapters(url);
    }
    fun getPlaybackTracker(url: String): IPlaybackTracker? {
        val baseClient = getContentClientOrNull(url) ?: return null;
        if (baseClient !is JSClient) {
            return baseClient.getPlaybackTracker(url);
        }
        val client = _trackerClientPool.getClientPooled(baseClient, 1);
        return client.getPlaybackTracker(url);
    }

    fun getContentRecommendations(url: String): IPager<IPlatformContent>? {
        val baseClient = getContentClientOrNull(url) ?: return null;
        if (baseClient !is JSClient) {
            return baseClient.getContentRecommendations(url);
        }
        val client = _mainClientPool.getClientPooled(baseClient);
        return client.getContentRecommendations(url);
    }

    fun hasEnabledChannelClient(url : String) : Boolean = getEnabledClients().any { it.isChannelUrl(url) };
    fun getChannelClient(url : String, exclude: List<String>? = null) : IPlatformClient = getChannelClientOrNull(url, exclude)
        ?: throw NoPlatformClientException("No client enabled that supports this channel url (${url})");
    fun getChannelClientOrNull(url : String, exclude: List<String>? = null) : IPlatformClient? =
        if(exclude == null)
            getEnabledClients().find { it.isChannelUrl(url) }
        else
            getEnabledClients().find { !exclude.contains(it.id) && it.isChannelUrl(url) };

    fun getChannel(url: String, updateSubscriptions: Boolean = true): Deferred<IPlatformChannel>  {
        Logger.i(TAG, "Platform - getChannel");
        val channel = StateSubscriptions.instance.getSubscription(url);
        return if(channel != null) {
            _scope.async { getChannelLive(url, updateSubscriptions) }; //_batchTaskGetChannel.execute(channel);
        } else {
            _scope.async { getChannelLive(url, updateSubscriptions) };
        }
    }

    fun getChannelContent(baseClient: IPlatformClient, channelUrl: String, isSubscriptionOptimized: Boolean = false, usePooledClients: Int = 0): IPager<IPlatformContent> {
        val clientCapabilities = baseClient.getChannelCapabilities();
        val client = if(usePooledClients > 1)
            _channelClientPool.getClientPooled(baseClient, usePooledClients);
        else baseClient;

        var lastStream: OffsetDateTime? = null;

        val pagerResult: IPager<IPlatformContent>;
        if(!clientCapabilities.hasType(ResultCapabilities.TYPE_MIXED) &&
                (   clientCapabilities.hasType(ResultCapabilities.TYPE_VIDEOS) ||
                    clientCapabilities.hasType(ResultCapabilities.TYPE_STREAMS) ||
                    clientCapabilities.hasType(ResultCapabilities.TYPE_LIVE) ||
                    clientCapabilities.hasType(ResultCapabilities.TYPE_POSTS)
                )) {
            val toQuery = mutableListOf<String>();
            if(clientCapabilities.hasType(ResultCapabilities.TYPE_VIDEOS))
                toQuery.add(ResultCapabilities.TYPE_VIDEOS);
            if(clientCapabilities.hasType(ResultCapabilities.TYPE_STREAMS))
                toQuery.add(ResultCapabilities.TYPE_STREAMS);
            if(clientCapabilities.hasType(ResultCapabilities.TYPE_LIVE))
                toQuery.add(ResultCapabilities.TYPE_LIVE);
            if(clientCapabilities.hasType(ResultCapabilities.TYPE_POSTS))
                toQuery.add(ResultCapabilities.TYPE_POSTS);

            if(isSubscriptionOptimized) {
                val sub = StateSubscriptions.instance.getSubscription(channelUrl);
                if(sub != null) {
                    if(!sub.shouldFetchStreams()) {
                        Logger.i(TAG, "Subscription [${sub.channel.name}:${channelUrl}] Last livestream > 7 days, skipping live streams [${sub.lastLiveStream.getNowDiffDays()} days ago]");
                        toQuery.remove(ResultCapabilities.TYPE_LIVE);
                    }
                    if(!sub.shouldFetchLiveStreams()) {
                        Logger.i(TAG, "Subscription [${sub.channel.name}:${channelUrl}] Last livestream > 15 days, skipping streams [${sub.lastLiveStream.getNowDiffDays()} days ago]");
                        toQuery.remove(ResultCapabilities.TYPE_STREAMS);
                    }
                    if(!sub.shouldFetchPosts()) {
                        Logger.i(TAG, "Subscription [${sub.channel.name}:${channelUrl}] Last livestream > 5 days, skipping posts [${sub.lastPost.getNowDiffDays()} days ago]");
                        toQuery.remove(ResultCapabilities.TYPE_POSTS);
                    }
                }
            }

            //Merged pager
            val pagers = toQuery
                .parallelStream()
                .map {
                    val results = client.getChannelContents(channelUrl, it, ResultCapabilities.ORDER_CHONOLOGICAL) ;

                    when(it) {
                        ResultCapabilities.TYPE_STREAMS -> {
                            val streamResults = results.getResults();
                            if(streamResults.size == 0)
                                lastStream = OffsetDateTime.MIN;
                            else
                                lastStream = results.getResults().firstOrNull()?.datetime;
                        }
                    }
                    return@map results;
                }
                .asSequence()
                .toList();

            val pager = MultiChronoContentPager(pagers.toTypedArray());
            pager.initialize();
            pagerResult = pager;
        }
        else
            pagerResult = client.getChannelContents(channelUrl, ResultCapabilities.TYPE_MIXED, ResultCapabilities.ORDER_CHONOLOGICAL);

        //Subscription optimization
        val sub = StateSubscriptions.instance.getSubscription(channelUrl);
        if(sub != null) {
            var hasChanges = false;
            val lastVideo = pagerResult.getResults().firstOrNull();

            if(lastVideo?.datetime != null && sub.lastVideo.getNowDiffDays() != lastVideo.datetime!!.getNowDiffDays()) {
                Logger.i(TAG, "Subscription [${channelUrl}] has new last video date [${lastVideo.datetime?.getNowDiffDays()} Days]");
                sub.lastVideo = lastVideo.datetime ?: sub.lastVideo;
                hasChanges = true;
            }

            if(lastStream != null && sub.lastLiveStream.getNowDiffDays() != lastStream!!.getNowDiffDays()) {
                Logger.i(TAG, "Subscription [${channelUrl}] has new last stream date [${lastStream!!.getNowDiffDays()} Days]");
                sub.lastLiveStream = lastStream!!;
                hasChanges = true;
            }

            val now = OffsetDateTime.now();
            val firstPage = pagerResult.getResults().filter { it.datetime != null && it.datetime!! < now }
            if(firstPage.size > 0) {
                val newestVideoDays = firstPage[0].datetime?.getNowDiffDays()?.toInt() ?: 0;
                val diffs = mutableListOf<Int>()
                for(i in (firstPage.size - 1) downTo  1) {
                    val currentVideoDays = firstPage[i].datetime?.getNowDiffDays();
                    val nextVideoDays = firstPage[i - 1].datetime?.getNowDiffDays();

                    if(currentVideoDays != null && nextVideoDays != null) {
                        val diff = nextVideoDays - currentVideoDays;
                        diffs.add(diff.toInt());
                    }
                }
                val averageDiff = if(diffs.size > 0)
                    newestVideoDays.coerceAtLeast(diffs.average().toInt())
                else
                    newestVideoDays;

                if(sub.uploadInterval != averageDiff) {
                    Logger.i(TAG, "Subscription [${channelUrl}] has new upload interval [${averageDiff} Days]");
                    sub.uploadInterval = averageDiff;
                    hasChanges = true;
                }
            }

            if(hasChanges)
                sub.save();
        }

        return pagerResult;
    }
    fun getChannelContent(channelUrl: String, isSubscriptionOptimized: Boolean = false, usePooledClients: Int = 0, ignorePlugins: List<String>? = null): IPager<IPlatformContent> {
        Logger.i(TAG, "Platform - getChannelVideos");
        val baseClient = getChannelClient(channelUrl, ignorePlugins);
        return getChannelContent(baseClient, channelUrl, isSubscriptionOptimized, usePooledClients);
    }
    fun getChannelContent(channelUrl: String, type: String?, ordering: String = ResultCapabilities.ORDER_CHONOLOGICAL): IPager<IPlatformContent> {
        val client = getChannelClient(channelUrl);
        return getChannelContent(client, channelUrl, type, ordering);
    }
    fun getChannelContent(baseClient: IPlatformClient, channelUrl: String, type: String?, ordering: String = ResultCapabilities.ORDER_CHONOLOGICAL): IPager<IPlatformContent> {
        val client = _channelClientPool.getClientPooled(baseClient, Settings.instance.subscriptions.getSubscriptionsConcurrency());
        return client.getChannelContents(channelUrl, type, ordering) ;
    }

    fun getChannelPlaylists(channelUrl: String): IPager<IPlatformPlaylist> {
        val client = getChannelClient(channelUrl);
        return client.getChannelPlaylists(channelUrl);
    }

    fun peekChannelContents(baseClient: IPlatformClient, channelUrl: String, type: String?): List<IPlatformContent> {
        val client = _channelClientPool.getClientPooled(baseClient, Settings.instance.subscriptions.getSubscriptionsConcurrency());
        return client.peekChannelContents(channelUrl, type) ;
    }

    fun getChannelLive(url: String, updateSubscriptions: Boolean = true): IPlatformChannel {
        val channel = getChannelClient(url).getChannel(url);

        if(updateSubscriptions && StateSubscriptions.instance.isSubscribed(channel))
            StateSubscriptions.instance.updateSubscriptionChannel(channel);

        return channel
    }

    fun getChannelUrlByClaim(claimType: Int, claimValues: Map<Int, String>): String? {
        val client = getClientByClaimTypeOrNull(claimType) ?: return null;
        return client.getChannelUrlByClaim(claimType, claimValues);
    }
    fun resolveChannelUrlByClaimTemplates(claimType: Int, claimValues: Map<Int, String>): String? {
        for(client in getClientsByClaimType(claimType).filter { it is JSClient }) {
            val url = (client as JSClient).resolveChannelUrlByClaimTemplates(claimType, claimValues);
            if(!url.isNullOrEmpty())
                return url;
        }
        return null;
    }

    fun resolveChannelUrlsByClaimTemplates(claimType: Int, claimValues: Map<Int, String>): List<String> {
        val urls = arrayListOf<String>();
        for(client in getClientsByClaimType(claimType).filter { it is JSClient }) {
            val res = (client as JSClient).resolveChannelUrlsByClaimTemplates(claimType, claimValues);
            urls.addAll(res);
        }
        return urls;
    }

    fun hasEnabledPlaylistClient(url: String) : Boolean = getEnabledClients().any { it.isPlaylistUrl(url) };
    fun getPlaylistClientOrNull(url: String): IPlatformClient? = getEnabledClients().find { it.isPlaylistUrl(url) }
    fun getPlaylistClient(url: String): IPlatformClient = getEnabledClients().find { it.isPlaylistUrl(url) }
        ?: throw NoPlatformClientException("No client enabled that supports this playlist url (${url})");
    fun getPlaylist(url: String): IPlatformPlaylistDetails {
        return getPlaylistClient(url).getPlaylist(url);
    }

    //Comments
    fun getComments(content: IPlatformContentDetails): IPager<IPlatformComment> {
        val client = getContentClient(content.url);
        val pager = content.getComments(client);

        return pager ?: getComments(content.url);
    }
    fun getComments(url: String): IPager<IPlatformComment> {
        Logger.i(TAG, "Platform - getComments");
        val client = getContentClient(url);
        if(!client.capabilities.hasGetComments)
            return EmptyPager();

        if(!StateApp.instance.privateMode)
            return client.fromPool(_mainClientPool).getComments(url);
        else
            return client.fromPool(_privateClientPool).getComments(url);
    }
    fun getSubComments(comment: IPlatformComment): IPager<IPlatformComment> {
        Logger.i(TAG, "Platform - getSubComments");
        val client = getContentClient(comment.contextUrl);
        return client.getSubComments(comment);
    }

    fun getLiveEvents(url: String): IPager<IPlatformLiveEvent>? {
        Logger.i(TAG, "Platform - getLiveChat");
        var client = getContentClient(url);

        if(!StateApp.instance.privateMode)
            return client.fromPool(_liveEventClientPool).getLiveEvents(url);
        else
            return client.fromPool(_privateClientPool).getLiveEvents(url);
    }
    fun getLiveChatWindow(url: String): ILiveChatWindowDescriptor? {
        Logger.i(TAG, "Platform - getLiveChat");
        var client = getContentClient(url);
        return client.getLiveChatWindow(url);
    }


    fun injectDevPlugin(source: SourcePluginConfig, script: String): String? {
        var devId: String? = null;
        synchronized(_clientsLock) {
            val enabledExisting = _enabledClients.filter { it is DevJSClient };
            val isEnabled = !enabledExisting.isEmpty()

            for (enabled in enabledExisting) {
                enabled.disable();
            }

            //Remove existing dev clients
            _enabledClients.removeIf { it is DevJSClient };
            _availableClients.removeIf { it is DevJSClient };

            source.id = StateDeveloper.DEV_ID;
            val newClient = DevJSClient(StateApp.instance.context, source, script);
            devId = newClient.devID;
            try {
                StateDeveloper.instance.initializeDev(devId!!);
                if (isEnabled) {
                    _enabledClients.add(newClient);
                    newClient.initialize();
                }
                _availableClients.add(newClient);
            } catch (ex: Exception) {
                Logger.e("StatePlatform", "Failed to initialize DevPlugin: ${ex.message}", ex);
                StateDeveloper.instance.logDevException(devId!!, "Failed to initialize due to: ${ex.message}");
            }
        }
        onDevSourceChanged.emit();
        return devId;
    }

    //TODO: Be careful with calling this unless you know for sure you're not gonna need the current app state anymore
    fun disableAllClients() {
        synchronized(_clientsLock) {
            val enabledClients = _enabledClients;
            _enabledClients.clear();
            for(enabled in enabledClients) {
                Logger.i(TAG, "Disabling plugin [${enabled.name}]");
                try {
                    enabled.disable();
                }
                catch (ex: Throwable) {}
            }
        }
    }



    companion object {
        private var _instance : StatePlatform? = null;
        val instance : StatePlatform
            get(){
            if(_instance == null)
                _instance = StatePlatform();
            return _instance!!;
        };

        fun finish() {
            _instance?.let {
                _instance = null;
                it._scope.cancel("PlatformState finished");
            }
        }
    }
}