package com.futo.platformplayer.subscription

import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.models.Subscription
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ForkJoinPool

class SmartSubscriptionAlgorithm(
    scope: CoroutineScope,
    allowFailure: Boolean = false,
    withCacheFallback: Boolean = true,
    threadPool: ForkJoinPool? = null
): SubscriptionFetchAlgorithm(scope, allowFailure, withCacheFallback, threadPool) {
    override fun countRequests(subs: Map<Subscription, List<String>>): Map<JSClient, Int> {
        TODO("Not yet implemented")
    }

    override fun getSubscriptions(subs: Map<Subscription, List<String>>): Result {
        TODO("Not yet implemented")
    }
}