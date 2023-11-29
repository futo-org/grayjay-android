package com.futo.platformplayer.states

import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.SerializedPlatformContent
import com.futo.platformplayer.api.media.structures.DedupContentPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.api.media.structures.MultiChronoContentPager
import com.futo.platformplayer.api.media.structures.PlatformContentPager
import com.futo.platformplayer.cache.ChannelContentCache
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.resolveChannelUrl
import com.futo.platformplayer.serializers.PlatformContentSerializer
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.db.ManagedDBStore
import com.futo.platformplayer.stores.db.types.DBChannelCache
import com.futo.platformplayer.stores.db.types.DBHistory
import com.futo.platformplayer.toSafeFileName
import java.time.OffsetDateTime

class StateCache {
    private val _channelCache = ManagedDBStore.create("channelCache", DBChannelCache.Descriptor(), PlatformContentSerializer())
        .load();

    fun clear() {
        _channelCache.deleteAll();
    }
    fun clearToday() {
        val today = _channelCache.queryGreater(DBChannelCache.Index::datetime, OffsetDateTime.now().toEpochSecond());
        for(content in today)
            _channelCache.delete(content);
    }

    fun getChannelCachePager(channelUrl: String): IPager<IPlatformContent> {
        return _channelCache.queryPager(DBChannelCache.Index::channelUrl, channelUrl, 20) {
            it.obj;
        }
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

        val pagers = MultiChronoContentPager(allUrls.map { getChannelCachePager(it) }, false, 20);
        return DedupContentPager(pagers, StatePlatform.instance.getEnabledClients().map { it.id });
    }


    fun getCachedContent(url: String): DBChannelCache.Index? {
        return _channelCache.query(DBChannelCache.Index::url, url).firstOrNull();
    }

    fun uncacheContent(content: SerializedPlatformContent) {
        val item = getCachedContent(content.url);
        if(item != null)
            _channelCache.delete(item);
    }
    fun cacheContents(contents: List<IPlatformContent>): List<IPlatformContent> {
        return contents.filter { cacheContent(it) };
    }
    fun cacheContent(content: IPlatformContent, doUpdate: Boolean = false): Boolean {
        if(content.author.url.isEmpty())
            return false;

        val serialized = SerializedPlatformContent.fromContent(content);
        val existing = getCachedContent(content.url);

        if(existing != null && doUpdate) {
            _channelCache.update(existing.id!!, serialized);
            return true;
        }
        else if(existing == null) {
            _channelCache.insert(serialized);
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
    }
}