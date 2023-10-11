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
import com.futo.platformplayer.toSafeFileName
import com.futo.polycentric.core.toUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChannelContentCache {
    val _channelCacheDir = FragmentedStorage.getOrCreateDirectory("channelCache");
    val _channelContents = HashMap(_channelCacheDir.listFiles()
        .filter { it.isDirectory }
        .associate { Pair(it.name, FragmentedStorage.storeJson<SerializedPlatformContent>(_channelCacheDir, it.name, PlatformContentSerializer())
            .withoutBackup()
            .load()) });

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
        val subs = StateSubscriptions.instance.getSubscriptions();
        val allUrls = subs.map {
            val otherUrls =  PolycentricCache.instance.getCachedProfile(it.channel.url)?.profile?.ownedClaims?.mapNotNull { c -> c.claim.resolveChannelUrl() } ?: listOf();
            if(!otherUrls.contains(it.channel.url))
                return@map listOf(listOf(it.channel.url), otherUrls).flatten();
            else
                return@map otherUrls;
        }.flatten().distinct();
        val validSubIds = allUrls.map { it.toSafeFileName() }.toHashSet();

        val validStores = _channelContents
            .filter { validSubIds.contains(it.key) }
            .map { it.value };

        val items = validStores.flatMap { it.getItems() }
            .sortedByDescending { it.datetime };

        return DedupContentPager(PlatformContentPager(items, Math.min(150, items.size)), StatePlatform.instance.getEnabledClients().map { it.id });
    }

    fun cacheVideos(contents: List<IPlatformContent>): List<IPlatformContent> {
        return contents.filter { cacheContent(it) };
    }
    fun cacheContent(content: IPlatformContent, doUpdate: Boolean = false): Boolean {
        if(content.author.url.isEmpty())
            return false;

        val channelId = content.author.url.toSafeFileName();
        val store = synchronized(_channelContents) {
            var channelStore = _channelContents.get(channelId);
            if(channelStore == null) {
                Logger.i(TAG, "New Subscription Cache for channel ${content.author.name}");
                channelStore = FragmentedStorage.storeJson<SerializedPlatformContent>(_channelCacheDir, channelId, PlatformContentSerializer()).load();
                _channelContents.put(channelId, channelStore);
            }
            return@synchronized channelStore;
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

    companion object {
        private val TAG = "ChannelCache";

        private val _lock = Object();
        private var _instance: ChannelContentCache? = null;
        val instance: ChannelContentCache get() {
            synchronized(_lock) {
                if(_instance == null)
                    _instance = ChannelContentCache();
                return _instance!!;
            }
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
                    val newCacheItems = instance.cacheVideos(results);
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
                    val newCacheItems = instance.cacheVideos(results);
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