package com.futo.platformplayer.subscription

import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.structures.DedupContentPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.api.media.structures.PlatformContentPager
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.resolveChannelUrl
import com.futo.platformplayer.states.StateCache
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.toSafeFileName
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ForkJoinPool

class CachedSubscriptionAlgorithm(pageSize: Int = 150, scope: CoroutineScope, allowFailure: Boolean = false, withCacheFallback: Boolean = true, threadPool: ForkJoinPool? = null)
        : SubscriptionFetchAlgorithm(scope, allowFailure, withCacheFallback, threadPool) {

    private val _pageSize: Int = pageSize;

    override fun countRequests(subs: Map<Subscription, List<String>>): Map<JSClient, Int> {
        return mapOf<JSClient, Int>();
    }

    override fun getSubscriptions(subs: Map<Subscription, List<String>>): Result {
        val validSubIds = subs.flatMap { it.value } .map { it.toSafeFileName() }.toHashSet();

        /*
        val validStores = StateCache.instance._channelContents
            .filter { validSubIds.contains(it.key) }
            .map { it.value };*/

        /*
        val items = validStores.flatMap { it.getItems() }
            .sortedByDescending { it.datetime };
        */

        return Result(DedupContentPager(StateCache.instance.getChannelCachePager(subs.flatMap { it.value }.distinct()), StatePlatform.instance.getEnabledClients().map { it.id }), listOf());
    }
}