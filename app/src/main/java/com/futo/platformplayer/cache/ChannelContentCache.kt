package com.futo.platformplayer.cache

import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.SerializedPlatformContent
import com.futo.platformplayer.api.media.structures.DedupContentPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.api.media.structures.PlatformContentPager
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.resolveChannelUrl
import com.futo.platformplayer.serializers.PlatformContentSerializer
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.v2.ManagedStore
import com.futo.platformplayer.toSafeFileName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import kotlin.streams.toList
import kotlin.system.measureTimeMillis

class ChannelContentCache {
    private val _targetCacheSize = 3000;
    val _channelCacheDir = FragmentedStorage.getOrCreateDirectory("channelCache");
    val _channelContents: HashMap<String, ManagedStore<SerializedPlatformContent>>;
    init {
        val allFiles = _channelCacheDir.listFiles() ?: arrayOf();
        val initializeTime = measureTimeMillis {
            _channelContents = HashMap(allFiles
                .filter { it.isDirectory }
                .parallelStream().map {
                    Pair(it.name, FragmentedStorage.storeJson(_channelCacheDir, it.name, PlatformContentSerializer())
                            .withoutBackup()
                            .load())
                }.toList().associate { it })
        }
        val minDays = OffsetDateTime.now().minusDays(10);
        val totalItems = _channelContents.map { it.value.count() }.sum();
        val toTrim = totalItems - _targetCacheSize;
        val trimmed: Int;
        if(toTrim > 0) {
            val redundantContent = _channelContents.flatMap { it.value.getItems().filter { it.datetime != null && it.datetime!!.isBefore(minDays) }.drop(9) }
                .sortedBy { it.datetime!! }.take(toTrim);
            for(content in redundantContent)
                uncacheContent(content);
            trimmed = redundantContent.size;
        }
        else trimmed = 0;
        Logger.i(TAG, "ChannelContentCache time: ${initializeTime}ms channels: ${allFiles.size}, videos: ${totalItems}, trimmed: ${trimmed}, total: ${totalItems - trimmed}");
    }

    fun getChannelCachePager(channelUrl: String): PlatformContentPager {
        val validID = channelUrl.toSafeFileName();

        val validStores = _channelContents
            .filter { it.key == validID }
            .map { it.value };

        val items = validStores.flatMap { it.getItems() }
            .sortedByDescending { it.datetime };
        return PlatformContentPager(items, Math.min(150, items.size));
    }
    fun getSubscriptionCachePager(): DedupContentPager {
        Logger.i(TAG, "Subscriptions CachePager get subscriptions");
        val subs = StateSubscriptions.instance.getSubscriptions();
        Logger.i(TAG, "Subscriptions CachePager polycentric urls");
        val allUrls = subs.map {
            val otherUrls =  PolycentricCache.instance.getCachedProfile(it.channel.url)?.profile?.ownedClaims?.mapNotNull { c -> c.claim.resolveChannelUrl() } ?: listOf();
            if(!otherUrls.contains(it.channel.url))
                return@map listOf(listOf(it.channel.url), otherUrls).flatten();
            else
                return@map otherUrls;
        }.flatten().distinct();
        Logger.i(TAG, "Subscriptions CachePager compiling");
        val validSubIds = allUrls.map { it.toSafeFileName() }.toHashSet();

        val validStores = _channelContents
            .filter { validSubIds.contains(it.key) }
            .map { it.value };

        val items = validStores.flatMap { it.getItems() }
            .sortedByDescending { it.datetime };

        return DedupContentPager(PlatformContentPager(items, Math.min(150, items.size)), StatePlatform.instance.getEnabledClients().map { it.id });
    }

    fun uncacheContent(content: SerializedPlatformContent) {
        val store = getContentStore(content);
        store?.delete(content);
    }
    fun cacheContents(contents: List<IPlatformContent>): List<IPlatformContent> {
        return contents.filter { cacheContent(it) };
    }
    fun cacheContent(content: IPlatformContent, doUpdate: Boolean = false): Boolean {
        if(content.author.url.isEmpty())
            return false;

        val channelId = content.author.url.toSafeFileName();
        val store = getContentStore(channelId).let {
            if(it == null) {
                Logger.i(TAG, "New Channel Cache for channel ${content.author.name}");
                val store = FragmentedStorage.storeJson<SerializedPlatformContent>(_channelCacheDir, channelId, PlatformContentSerializer()).load();
                _channelContents.put(channelId, store);
                return@let store;
            }
            else return@let it;
        }
        val serialized = SerializedPlatformContent.fromContent(content);
        val existing = store.findItems { it.url == content.url };

        if(existing.isEmpty() || doUpdate) {
            if(existing.isNotEmpty())
                existing.forEach { store.delete(it) };

            store.save(serialized);
        }

        return existing.isEmpty();
    }

    private fun getContentStore(content: IPlatformContent): ManagedStore<SerializedPlatformContent>? {
        val channelId = content.author.url.toSafeFileName();
        return getContentStore(channelId);
    }
    private fun getContentStore(channelId: String): ManagedStore<SerializedPlatformContent>? {
        return synchronized(_channelContents) {
            var channelStore = _channelContents.get(channelId);
            return@synchronized channelStore;
        }
    }

    companion object {
        private val TAG = "ChannelCache";

        private val _lock = Object();
        private var _instance: ChannelContentCache? = null;
        val instance: ChannelContentCache get() {
            synchronized(_lock) {
                if(_instance == null) {
                    _instance = ChannelContentCache();
                }
            }
            return _instance!!;
        }

        fun cachePagerResults(scope: CoroutineScope, pager: IPager<IPlatformContent>, onNewCacheHit: ((IPlatformContent)->Unit)? = null): IPager<IPlatformContent> {
            return ChannelVideoCachePager(pager, scope, onNewCacheHit);
        }
    }

    class ChannelVideoCachePager(val pager: IPager<IPlatformContent>, private val scope: CoroutineScope, private val onNewCacheItem: ((IPlatformContent)->Unit)? = null): IPager<IPlatformContent> {

        init {
            val results = pager.getResults();

            Logger.i(TAG, "Caching ${results.size} subscription initial results [${pager.hashCode()}]");
            scope.launch(Dispatchers.IO) {
                try {
                    val newCacheItems = instance.cacheContents(results);
                    if(onNewCacheItem != null)
                        newCacheItems.forEach { onNewCacheItem!!(it) }
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

            Logger.i(TAG, "Caching ${results.size} subscription results");
            scope.launch(Dispatchers.IO) {
                try {
                    val newCacheItems = instance.cacheContents(results);
                    if(onNewCacheItem != null)
                        newCacheItems.forEach { onNewCacheItem!!(it) }
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to cache videos.", e);
                }
            }
        }

        override fun getResults(): List<IPlatformContent> {
            val results = pager.getResults();

            return results;
        }

    }
}