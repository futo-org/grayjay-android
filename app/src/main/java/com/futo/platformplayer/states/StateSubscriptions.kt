package com.futo.platformplayer.states

import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.channels.SerializedChannel
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.structures.*
import com.futo.platformplayer.api.media.structures.ReusablePager.Companion.asReusable
import com.futo.platformplayer.cache.ChannelContentCache
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.engine.exceptions.PluginException
import com.futo.platformplayer.engine.exceptions.ScriptCaptchaRequiredException
import com.futo.platformplayer.engine.exceptions.ScriptCriticalException
import com.futo.platformplayer.exceptions.ChannelException
import com.futo.platformplayer.findNonRuntimeException
import com.futo.platformplayer.fragment.mainactivity.main.PolycentricProfile
import com.futo.platformplayer.getNowDiffDays
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.resolveChannelUrl
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.SubscriptionStorage
import com.futo.platformplayer.stores.v2.ReconstructStore
import com.futo.platformplayer.stores.v2.ManagedStore
import com.futo.platformplayer.subscription.SubscriptionFetchAlgorithm
import com.futo.platformplayer.subscription.SubscriptionFetchAlgorithms
import kotlinx.coroutines.*
import java.time.OffsetDateTime
import java.util.concurrent.ExecutionException
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import kotlin.collections.ArrayList
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.streams.toList
import kotlin.system.measureTimeMillis

/***
 * Used to maintain subscriptions
 */
class StateSubscriptions {
    private val _subscriptions = FragmentedStorage.storeJson<Subscription>("subscriptions")
        .withUnique { it.channel.url }
        .withRestore(object: ReconstructStore<Subscription>(){
            override fun toReconstruction(obj: Subscription): String =
                obj.channel.url;
            override suspend fun toObject(id: String, backup: String, reconstructionBuilder: Builder): Subscription =
                Subscription(SerializedChannel.fromChannel(StatePlatform.instance.getChannelLive(backup, false)));
        }).load();
    private val _subscriptionsPool = ForkJoinPool(Settings.instance.subscriptions.getSubscriptionsConcurrency());
    private val _legacySubscriptions = FragmentedStorage.get<SubscriptionStorage>();


    private var _globalSubscriptionsLock = Object();
    private var _globalSubscriptionFeed: ReusablePager<IPlatformContent>? = null;
    var isGlobalUpdating: Boolean = false
        private set;
    var globalSubscriptionExceptions: List<Throwable> = listOf()
        private set;

    private val _algorithmSubscriptions = SubscriptionFetchAlgorithms.SMART;

    private var _lastGlobalSubscriptionProgress: Int = 0;
    private var _lastGlobalSubscriptionTotal: Int = 0;
    val onGlobalSubscriptionsUpdateProgress = Event2<Int, Int>();
    val onGlobalSubscriptionsUpdated = Event0();
    val onGlobalSubscriptionsUpdatedOnce = Event1<Throwable?>();
    val onGlobalSubscriptionsException = Event1<List<Throwable>>();

    val onSubscriptionsChanged = Event2<List<Subscription>, Boolean>();

    fun getOldestUpdateTime(): OffsetDateTime {
        val subs = getSubscriptions();
        if(subs.size == 0)
            return OffsetDateTime.now();
        else
            return subs.minOf { it.lastVideoUpdate };
    }
    fun getGlobalSubscriptionProgress(): Pair<Int, Int> {
        return Pair(_lastGlobalSubscriptionProgress, _lastGlobalSubscriptionTotal);
    }
    fun updateSubscriptionFeed(scope: CoroutineScope, onlyIfNull: Boolean = false, onProgress: ((Int, Int)->Unit)? = null) {
        Logger.v(TAG, "updateSubscriptionFeed");
        scope.launch(Dispatchers.IO) {
            synchronized(_globalSubscriptionsLock) {
                if (isGlobalUpdating || (onlyIfNull && _globalSubscriptionFeed != null)) {
                    Logger.i(TAG, "Already updating subscriptions or not required")
                    return@launch;
                }
                isGlobalUpdating = true;
            }
            try {
                val subsResult = getSubscriptionsFeedWithExceptions(true, true, scope, { progress, total ->
                    _lastGlobalSubscriptionProgress = progress;
                    _lastGlobalSubscriptionTotal = total;
                    onGlobalSubscriptionsUpdateProgress.emit(progress, total);
                    onProgress?.invoke(progress, total);
                });
                if (subsResult.second.any()) {
                    globalSubscriptionExceptions = subsResult.second;
                    onGlobalSubscriptionsException.emit(subsResult.second);
                }
                _globalSubscriptionFeed = subsResult.first.asReusable();
                synchronized(_globalSubscriptionsLock) {
                    onGlobalSubscriptionsUpdated.emit();
                    onGlobalSubscriptionsUpdatedOnce.emit(null);
                    onGlobalSubscriptionsUpdatedOnce.clear();
                }
            }
            catch (e: Throwable) {
                synchronized(_globalSubscriptionsLock) {
                    onGlobalSubscriptionsUpdatedOnce.emit(e);
                    onGlobalSubscriptionsUpdatedOnce.clear();
                }
                Logger.e(TAG, "Failed to update subscription feed.", e);
            }
            finally {
                isGlobalUpdating = false;
            }
        };
    }
    fun clearSubscriptionFeed() {
        synchronized(_globalSubscriptionsLock) {
            _globalSubscriptionFeed = null;
        }
    }

    private var loadIndex = 0;
    suspend fun getGlobalSubscriptionFeed(scope: CoroutineScope, updated: Boolean): IPager<IPlatformContent> {
        //Get Subscriptions only if null
        updateSubscriptionFeed(scope, !updated);

        val evRef = Object();
        val result = suspendCoroutine {
            synchronized(_globalSubscriptionsLock) {
                if (_globalSubscriptionFeed != null && !updated) {
                    Logger.i(TAG, "Subscriptions got feed preloaded");
                    it.resumeWith(Result.success(_globalSubscriptionFeed!!.getWindow()));
                } else {
                    val loadIndex = loadIndex++;
                    Logger.i(TAG, "[${loadIndex}] Starting await update");
                    onGlobalSubscriptionsUpdatedOnce.subscribe(evRef) {ex ->
                        Logger.i(TAG, "[${loadIndex}] Subscriptions got feed after update");
                        if(ex != null)
                            it.resumeWithException(ex);
                        else if (_globalSubscriptionFeed != null)
                            it.resumeWith(Result.success(_globalSubscriptionFeed!!.getWindow()));
                        else
                            it.resumeWithException(IllegalStateException("No subscription pager after change? Illegal null set on global subscriptions"))
                    }
                }
            }
        };
        return result;
    }

    suspend fun updateSubscriptions(doSave: Boolean = true) {
        for (sub in _subscriptions.getItems()) {
            Logger.i(TAG, "Updating channel ${sub.channel.name} with url ${sub.channel.url}");
            val updatedSub = StatePlatform.instance.getChannel(sub.channel.url, false).await();
            sub.updateChannel(updatedSub);
            if(doSave)
                _subscriptions.save(sub);
        }
    }

    fun getSubscription(url: String) : Subscription? {
        synchronized(_subscriptions) {
            return _subscriptions.findItem { it.channel.url == url || it.channel.urlAlternatives.contains(url) };
        }
    }
    fun saveSubscription(sub: Subscription) {
        _subscriptions.save(sub, false, true);
    }
    fun saveSubscriptionAsync(sub: Subscription) {
        _subscriptions.saveAsync(sub, false, true);
    }
    fun getSubscriptionCount(): Int {
        synchronized(_subscriptions) {
            return _subscriptions.getItems().size;
        }
    }
    fun getSubscriptions(): List<Subscription> {
        return _subscriptions.getItems();
    }

    fun addSubscription(channel : IPlatformChannel) : Subscription {
        val subObj = Subscription(SerializedChannel.fromChannel(channel));
        _subscriptions.save(subObj);
        onSubscriptionsChanged.emit(getSubscriptions(), true);
        return subObj;
    }
    fun removeSubscription(url: String) : Subscription? {
        var sub : Subscription? = getSubscription(url);
        if(sub != null) {
            _subscriptions.delete(sub);
            onSubscriptionsChanged.emit(getSubscriptions(), false);
        }
        return sub;
    }

    fun isSubscribed(channel: IPlatformChannel): Boolean {
        val urls = (listOf(channel.url) + channel.urlAlternatives).distinct();
        return isSubscribed(urls);
    }
    fun isSubscribed(url: String) : Boolean {
        return isSubscribed(listOf(url));
    }
    fun isSubscribed(urls: List<String>) : Boolean {
        if(urls.isEmpty())
            return false;
        synchronized(_subscriptions) {
            if (_subscriptions.hasItem { urls.contains(it.channel.url) }) {
                return true;
            }

            //TODO: This causes issues, because what if the profile is not cached yet when the susbcribe button is loaded for example?
            val cachedProfile = PolycentricCache.instance.getCachedProfile(urls.first(), true)?.profile;
            if (cachedProfile != null) {
                return cachedProfile.ownedClaims.any { c -> _subscriptions.hasItem { s -> c.claim.resolveChannelUrl() == s.channel.url } };
            }

            return false;
        }
    }
    fun updateSubscriptionChannel(channel: IPlatformChannel, doSave: Boolean = true) {
        val sub = getSubscription(channel.url) ?: channel.urlAlternatives.firstNotNullOfOrNull { getSubscription(it) };
        if(sub != null) {
            sub.updateChannel(channel);
            if(doSave)
                _subscriptions.save(sub);
        }
    }

    fun getSubscriptionRequestCount(): Map<JSClient, Int> {
        return SubscriptionFetchAlgorithm.getAlgorithm(_algorithmSubscriptions, StateApp.instance.scope)
            .countRequests(getSubscriptions().associateWith { StatePolycentric.instance.getChannelUrls(it.channel.url, it.channel.id, true) });
    }

    fun getSubscriptionsFeedWithExceptions(allowFailure: Boolean = false, withCacheFallback: Boolean = false, cacheScope: CoroutineScope, onProgress: ((Int, Int)->Unit)? = null, onNewCacheHit: ((Subscription, IPlatformContent)->Unit)? = null): Pair<IPager<IPlatformContent>, List<Throwable>> {
        val algo = SubscriptionFetchAlgorithm.getAlgorithm(_algorithmSubscriptions, cacheScope, allowFailure, withCacheFallback, _subscriptionsPool);
        if(onNewCacheHit != null)
            algo.onNewCacheHit.subscribe(onNewCacheHit)

        algo.onProgress.subscribe { progress, total ->
            onProgress?.invoke(progress, total);
        }

        val usePolycentric = true;
        val subUrls = getSubscriptions().parallelStream().map {
            if(usePolycentric)
                Pair(it, StatePolycentric.instance.getChannelUrls(it.channel.url, it.channel.id));
            else
                Pair(it, listOf(it.channel.url));
        }.toList().associate { it };

        val result = algo.getSubscriptions(subUrls);
        return Pair(result.pager, result.exceptions);
    }

    //New Migration
    fun toMigrateCheck(): List<ManagedStore<*>> {
        return listOf(_subscriptions);
    }

    //Old migrate
    fun shouldMigrate(): Boolean {
        return _legacySubscriptions.subscriptions.any();
    }
    fun tryMigrateIfNecessary() {
        Logger.i(TAG, "MIGRATING SUBS");
        val oldSubs = _legacySubscriptions.subscriptions.toList();

        for(sub in oldSubs) {
            if(!this.isSubscribed(sub.channel.url)) {
                Logger.i(TAG, "MIGRATING ${sub.channel.url}");
                addSubscription(sub.channel);
            }
        }
        _legacySubscriptions.delete();
    }

    companion object {
        const val TAG = "StateSubscriptions";
        const val VERSION = 1;

        private var _instance : StateSubscriptions? = null;
        val instance : StateSubscriptions
            get(){
            if(_instance == null)
                _instance = StateSubscriptions();
            return _instance!!;
        };

        fun finish() {
            _instance?.let {
                _instance = null;
            }
        }
    }
}