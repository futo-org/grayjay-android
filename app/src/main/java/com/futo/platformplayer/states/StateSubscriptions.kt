package com.futo.platformplayer.states

import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.channels.SerializedChannel
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.structures.*
import com.futo.platformplayer.api.media.structures.ReusablePager.Companion.asReusable
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.constructs.Event3
import com.futo.platformplayer.functional.CentralizedFeed
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.ImportCache
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.models.SubscriptionGroup
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
import java.util.concurrent.ForkJoinPool
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.streams.asSequence

/***
 * Used to maintain subscriptions
 */
class StateSubscriptions {
    private val _subscriptions = FragmentedStorage.storeJson<Subscription>("subscriptions")
        .withUnique { it.channel.url }
        .withRestore(object: ReconstructStore<Subscription>(){
            override fun toReconstruction(obj: Subscription): String =
                obj.channel.url;
            override suspend fun toObject(id: String, backup: String, reconstructionBuilder: Builder, importCache: ImportCache?): Subscription =
                Subscription(importCache?.channels?.find { it.isSameUrl(backup) } ?: SerializedChannel.fromChannel(StatePlatform.instance.getChannelLive(backup, false)));
        }).load();
    private val _subscriptionOthers = FragmentedStorage.storeJson<Subscription>("subscriptions_others")
        .withUnique { it.channel.url }
        .load();
    private val _subscriptionsPool = ForkJoinPool(Settings.instance.subscriptions.getSubscriptionsConcurrency());
    private val _legacySubscriptions = FragmentedStorage.get<SubscriptionStorage>();

    private val _algorithmSubscriptions = SubscriptionFetchAlgorithms.SMART;

    val global: CentralizedFeed = CentralizedFeed();
    val feeds: HashMap<String, CentralizedFeed> = hashMapOf();
    val onFeedProgress = Event3<String?, Int, Int>();

    val onSubscriptionsChanged = Event2<List<Subscription>, Boolean>();

    init {
        global.onUpdateProgress.subscribe { progress, total ->
            onFeedProgress.emit(null, progress, total);
        }
    }

    fun getOldestUpdateTime(): OffsetDateTime {
        val subs = getSubscriptions();
        if(subs.size == 0)
            return OffsetDateTime.now();
        else
            return subs.minOf { it.lastVideoUpdate };
    }

    fun getFeed(id: String? = null, createIfNew: Boolean = false): CentralizedFeed? {
        if(id == null)
            return global;
        else {
            return synchronized(feeds) {
                var f = feeds[id];
                if(f == null && createIfNew) {
                    f = CentralizedFeed();
                    f.onUpdateProgress.subscribe { progress, total ->
                        onFeedProgress.emit(id, progress, total)
                    };
                    feeds[id] = f;
                }
                return@synchronized f;
            }
        }
    }

    fun getGlobalSubscriptionProgress(id: String? = null): Pair<Int, Int> {
        val feed = getFeed(id, false) ?: return Pair(0, 0);
        return Pair(feed.lastProgress, feed.lastTotal);
    }
    fun updateSubscriptionFeed(scope: CoroutineScope, onlyIfNull: Boolean = false, onProgress: ((Int, Int)->Unit)? = null, group: SubscriptionGroup? = null) {
        val feed = getFeed(group?.id, true) ?: return;
        Logger.v(TAG, "updateSubscriptionFeed");
        scope.launch(Dispatchers.IO) {
            synchronized(feed.lock) {
                if (feed.isGlobalUpdating || (onlyIfNull && feed.feed != null)) {
                    Logger.i(TAG, "Already updating subscriptions or not required")
                    return@launch;
                }
                feed.isGlobalUpdating = true;
            }
            try {
                val subsResult = getSubscriptionsFeedWithExceptions(true, true, scope, { progress, total ->
                    feed.lastProgress = progress;
                    feed.lastTotal = total;
                    feed.onUpdateProgress.emit(progress, total);
                    onProgress?.invoke(progress, total);
                }, null, group);
                if (subsResult.second.any()) {
                    feed.exceptions = subsResult.second;
                    feed.onException.emit(subsResult.second);
                }
                feed.feed = subsResult.first.asReusable();
                synchronized(feed.lock) {
                    feed.onUpdated.emit();
                    feed.onUpdatedOnce.emit(null);
                    feed.onUpdatedOnce.clear();
                }
            }
            catch (e: Throwable) {
                synchronized(feed.lock) {
                    feed.onUpdatedOnce.emit(e);
                    feed.onUpdatedOnce.clear();
                }
                Logger.e(TAG, "Failed to update subscription feed.", e);
            }
            finally {
                feed.isGlobalUpdating = false;
            }
        };
    }
    fun clearSubscriptionFeed(id: String? = null) {
        val feed = getFeed(id) ?: return;
        synchronized(feed.lock) {
            feed.feed = null;
        }
    }

    private var loadIndex = 0;
    suspend fun getGlobalSubscriptionFeed(scope: CoroutineScope, updated: Boolean, group: SubscriptionGroup? = null): IPager<IPlatformContent> {
        val feed = getFeed(group?.id, true) ?: return EmptyPager();
        //Get Subscriptions only if null
        updateSubscriptionFeed(scope, !updated, null, group);

        val evRef = Object();
        val result = suspendCoroutine {
            synchronized(feed.lock) {
                if (feed.feed != null && !updated) {
                    Logger.i(TAG, "Subscriptions got feed preloaded");
                    it.resumeWith(Result.success(feed.feed!!.getWindow()));
                } else {
                    val loadIndex = loadIndex++;
                    Logger.i(TAG, "[${loadIndex}] Starting await update");
                    feed.onUpdatedOnce.subscribe(evRef) { ex ->
                        Logger.i(TAG, "[${loadIndex}] Subscriptions got feed after update");
                        if(ex != null)
                            it.resumeWithException(ex);
                        else if (feed.feed != null)
                            it.resumeWith(Result.success(feed.feed!!.getWindow()));
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
    fun getSubscriptionOther(url: String) : Subscription? {
        synchronized(_subscriptionOthers) {
            return _subscriptionOthers.findItem { it.isChannel(url)};
        }
    }
    fun getSubscriptionOtherOrCreate(url: String) : Subscription {
        synchronized(_subscriptionOthers) {
            val sub = getSubscriptionOther(url);
            if(sub == null) {
                val newSub = Subscription(SerializedChannel(PlatformID.NONE, url, null, null, 0, null, url, mapOf()));
                newSub.isOther = true;
                _subscriptions.save(newSub);
                return newSub;
            }
            else return sub;
        }
    }
    fun saveSubscription(sub: Subscription) {
        _subscriptions.save(sub, false, true);
    }
    fun saveSubscriptionAsync(sub: Subscription) {
        _subscriptions.saveAsync(sub, false, true);
    }
    fun saveSubscriptionOther(sub: Subscription) {
        _subscriptionOthers.save(sub, false, true);
    }
    fun saveSubscriptionOtherAsync(sub: Subscription) {
        _subscriptionOthers.saveAsync(sub, false, true);
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

    fun getSubscriptionRequestCount(subGroup: SubscriptionGroup? = null): Map<JSClient, Int> {
        val subs = getSubscriptions();
        val emulatedSubs = subGroup?.let {
            it.urls.map {url ->
                subs.find { it.channel.url == url }
                    ?: getSubscriptionOtherOrCreate(url);
            };
        } ?: subs;
        return SubscriptionFetchAlgorithm.getAlgorithm(_algorithmSubscriptions, StateApp.instance.scope)
            .countRequests(emulatedSubs.associateWith { StatePolycentric.instance.getChannelUrls(it.channel.url, it.channel.id, true) });
    }

    fun getSubscriptionsFeedWithExceptions(allowFailure: Boolean = false, withCacheFallback: Boolean = false, cacheScope: CoroutineScope, onProgress: ((Int, Int)->Unit)? = null, onNewCacheHit: ((Subscription, IPlatformContent)->Unit)? = null, subGroup: SubscriptionGroup? = null): Pair<IPager<IPlatformContent>, List<Throwable>> {
        val algo = SubscriptionFetchAlgorithm.getAlgorithm(_algorithmSubscriptions, cacheScope, allowFailure, withCacheFallback, _subscriptionsPool);
        if(onNewCacheHit != null)
            algo.onNewCacheHit.subscribe(onNewCacheHit)

        algo.onProgress.subscribe { progress, total ->
            onProgress?.invoke(progress, total);
        }

        val subs = getSubscriptions();
        val emulatedSubs = subGroup?.let {
            it.urls.map {url ->
                subs.find { it.channel.url == url }
                    ?: getSubscriptionOtherOrCreate(url);
            };
        } ?: subs;


        val usePolycentric = true;
        val lock = Object();
        var polycentricBudget: Int = 10;
        val subUrls = emulatedSubs.parallelStream().map {
            if(usePolycentric) {
                val result = StatePolycentric.instance.getChannelUrlsWithUpdateResult(it.channel.url, it.channel.id, polycentricBudget <= 0, true);
                if(result.first) {
                    synchronized(lock) {
                        polycentricBudget--;
                    }
                }
                Pair(it, result.second);
            }
            else
                Pair(it, listOf(it.channel.url));
        }.asSequence()
            .toList()
            .associate { it };

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