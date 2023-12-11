package com.futo.platformplayer.subscription

import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.models.Subscription
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ForkJoinPool

abstract class SubscriptionFetchAlgorithm(
    val scope: CoroutineScope,
    val allowFailure: Boolean = false,
    val withCacheFallback: Boolean = true,
    private val _threadPool: ForkJoinPool? = null
) {
    val threadPool: ForkJoinPool get() = _threadPool ?: throw IllegalStateException("Require thread pool parameter");
    val onNewCacheHit = Event2<Subscription, IPlatformContent>();
    val onProgress = Event2<Int, Int>();

    fun countRequests(subs: List<Subscription>): Map<JSClient, Int> = countRequests(subs.associateWith { listOf(it.channel.url) });
    abstract fun countRequests(subs: Map<Subscription, List<String>>): Map<JSClient, Int>;

    fun getSubscriptions(subs: List<Subscription>): Result = getSubscriptions(subs.associateWith { listOf(it.channel.url) });
    abstract fun getSubscriptions(subs: Map<Subscription, List<String>>): Result;


    class Result(
        val pager: IPager<IPlatformContent>,
        val exceptions: List<Throwable>
    );

    companion object {
        public val TAG = "SubscriptionAlgorithm";

        fun getAlgorithm(algo: SubscriptionFetchAlgorithms, scope: CoroutineScope, allowFailure: Boolean = false, withCacheFallback: Boolean = false, pool: ForkJoinPool? = null): SubscriptionFetchAlgorithm {
            return when(algo) {
                SubscriptionFetchAlgorithms.CACHE -> CachedSubscriptionAlgorithm(scope, allowFailure, withCacheFallback, pool, 50);
                SubscriptionFetchAlgorithms.SIMPLE -> SimpleSubscriptionAlgorithm(scope, allowFailure, withCacheFallback, pool);
                SubscriptionFetchAlgorithms.SMART -> SmartSubscriptionAlgorithm(scope, allowFailure, withCacheFallback, pool);
            }
        }
    }
}