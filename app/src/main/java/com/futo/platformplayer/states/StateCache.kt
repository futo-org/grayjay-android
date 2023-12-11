package com.futo.platformplayer.states

import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.SerializedPlatformContent
import com.futo.platformplayer.api.media.structures.DedupContentPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.api.media.structures.MultiChronoContentPager
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.resolveChannelUrl
import com.futo.platformplayer.serializers.PlatformContentSerializer
import com.futo.platformplayer.stores.db.ManagedDBStore
import com.futo.platformplayer.stores.db.types.DBSubscriptionCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import kotlin.system.measureTimeMillis

class StateCache {
    private val _subscriptionCache = ManagedDBStore.create("subscriptionCache", DBSubscriptionCache.Descriptor(), PlatformContentSerializer())
        .load();

    val channelCacheStartupCount = _subscriptionCache.count();

    fun clear() {
        _subscriptionCache.deleteAll();
    }
    fun clearToday() {
        val today = _subscriptionCache.queryGreater(DBSubscriptionCache.Index::datetime, OffsetDateTime.now().toEpochSecond());
        for(content in today)
            _subscriptionCache.delete(content);
    }

    fun getChannelCachePager(channelUrl: String): IPager<IPlatformContent> {
        return _subscriptionCache.queryPager(DBSubscriptionCache.Index::channelUrl, channelUrl, 20) { it.obj }
    }
    fun getAllChannelCachePager(channelUrls: List<String>): IPager<IPlatformContent> {
        return _subscriptionCache.queryInPager(DBSubscriptionCache.Index::channelUrl, channelUrls, 20) { it.obj }
    }

    fun getChannelCachePager(channelUrls: List<String>, pageSize: Int = 20): IPager<IPlatformContent> {
        val pagers = MultiChronoContentPager(channelUrls.map { _subscriptionCache.queryPager(DBSubscriptionCache.Index::channelUrl, it, pageSize) {
            it.obj;
        } }, false, pageSize);
        return DedupContentPager(pagers, StatePlatform.instance.getEnabledClients().map { it.id });
    }
    fun getSubscriptionCachePager(): DedupContentPager {
        Logger.i(TAG, "Subscriptions CachePager get subscriptions");
        val subs = StateSubscriptions.instance.getSubscriptions();
        Logger.i(TAG, "Subscriptions CachePager polycentric urls");
        val allUrls = subs.map {
            val otherUrls = PolycentricCache.instance.getCachedProfile(it.channel.url)?.profile?.ownedClaims?.mapNotNull { c -> c.claim.resolveChannelUrl() } ?: listOf();
            if(!otherUrls.contains(it.channel.url))
                return@map listOf(listOf(it.channel.url), otherUrls).flatten();
            else
                return@map otherUrls;
        }.flatten().distinct();

        Logger.i(TAG, "Subscriptions CachePager get pagers");
        val pagers: List<IPager<IPlatformContent>>;

        val timeCacheRetrieving = measureTimeMillis {
            pagers = listOf(getAllChannelCachePager(allUrls));
        }

        Logger.i(TAG, "Subscriptions CachePager compiling (retrieved in ${timeCacheRetrieving}ms)");
        val pager = MultiChronoContentPager(pagers, false, 20);
        pager.initialize();
        Logger.i(TAG, "Subscriptions CachePager compiled");
        return DedupContentPager(pager, StatePlatform.instance.getEnabledClients().map { it.id });
    }


    fun getCachedContent(url: String): DBSubscriptionCache.Index? {
        return _subscriptionCache.query(DBSubscriptionCache.Index::url, url).firstOrNull();
    }

    fun uncacheContent(content: SerializedPlatformContent) {
        val item = getCachedContent(content.url);
        if(item != null)
            _subscriptionCache.delete(item);
    }
    fun cacheContents(contents: List<IPlatformContent>, doUpdate: Boolean = false): List<IPlatformContent> {
        return contents.filter { cacheContent(it, doUpdate) };
    }
    fun cacheContent(content: IPlatformContent, doUpdate: Boolean = false): Boolean {
        if(content.author.url.isEmpty())
            return false;

        val serialized = SerializedPlatformContent.fromContent(content);
        val existing = getCachedContent(content.url);

        if(existing != null && doUpdate) {
            _subscriptionCache.update(existing.id!!, serialized);
            return false;
        }
        else if(existing == null) {
            _subscriptionCache.insert(serialized);
            return true;
        }

        return false;
    }


    companion object {
        private val TAG = "StateCache";

        private var _instance : StateCache? = null;
        val instance : StateCache
            get(){
                if(_instance == null)
                    _instance = StateCache();
                return _instance!!;
            };

        fun finish() {
            _instance?.let {
                _instance = null;
            }
        }


        fun cachePagerResults(scope: CoroutineScope, pager: IPager<IPlatformContent>, onNewCacheHit: ((IPlatformContent)->Unit)? = null): IPager<IPlatformContent> {
            return ChannelContentCachePager(pager, scope, onNewCacheHit);
        }
    }
    class ChannelContentCachePager(val pager: IPager<IPlatformContent>, private val scope: CoroutineScope, private val onNewCacheItem: ((IPlatformContent)->Unit)? = null): IPager<IPlatformContent> {

        init {
            val results = pager.getResults();

            Logger.i(TAG, "Caching ${results.size} subscription initial results [${pager.hashCode()}]");
            scope.launch(Dispatchers.IO) {
                try {
                    val newCacheItems = instance.cacheContents(results, true);

                    onNewCacheItem?.let { f ->
                        newCacheItems.forEach { f(it) }
                    }
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to cache videos.", e);
                }
            }
        }

        override fun hasMorePages(): Boolean {
            return pager.hasMorePages();
        }

        override fun nextPage() {
            pager.nextPage();
            val results = pager.getResults();

            scope.launch(Dispatchers.IO) {
                try {
                    val newCacheItemsCount: Int;
                    val ms = measureTimeMillis {
                        val newCacheItems = instance.cacheContents(results, true);
                        newCacheItemsCount = newCacheItems.size;
                        onNewCacheItem?.let { f ->
                            newCacheItems.forEach { f(it) }
                        }
                    }
                    Logger.i(TAG, "Caching ${results.size} subscription results, updated $newCacheItemsCount (${ms}ms)");
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to cache ${results.size} videos.", e);
                }
            }
        }

        override fun getResults(): List<IPlatformContent> {
            val results = pager.getResults();

            return results;
        }

    }
}