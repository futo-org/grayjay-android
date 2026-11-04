package com.futo.platformplayer.tv.subscriptions

import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.models.channels.SerializedChannel
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.fragment.mainactivity.main.SubscriptionsFeedFragment.SubscriptionsFeedView.FeedFilterSettings
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateCache
import com.futo.platformplayer.states.StateHistory
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StateSubscriptions
import com.futo.platformplayer.stores.FragmentedStorage
import java.time.OffsetDateTime
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal data class TvSubscriptionFilters(
    val videos: Boolean = true,
    val live: Boolean = true,
    val planned: Boolean = false,
    val watched: Boolean = true
) {
    fun includes(video: IPlatformVideo, isWatched: (IPlatformVideo) -> Boolean): Boolean {
        if (!watched && video.duration > 0 && isWatched(video)) return false
        if (!planned && video.datetime?.isAfter(OffsetDateTime.now().plusMinutes(5)) == true) return false
        return if (video.isLive) live else videos
    }
}

internal data class TvSubscriptionFeed(val pager: IPager<IPlatformContent>, val failures: Int = 0)

internal class TvSubscriptionRateLimit(val sources: List<String>) : Exception()

internal interface TvSubscriptionsStore {
    fun channels(): List<SerializedChannel>

    fun filters(): TvSubscriptionFilters

    fun saveFilters(filters: TvSubscriptionFilters)

    fun watched(video: IPlatformVideo): Boolean

    fun hasEnabledSources(): Boolean

    fun refreshOnOpen(changed: Boolean): Boolean

    suspend fun cached(): IPager<IPlatformContent>

    suspend fun refresh(bypassRateLimit: Boolean): TvSubscriptionFeed

    fun observe(changed: () -> Unit, progress: (Int, Int) -> Unit): () -> Unit

    companion object {
        fun platform(): TvSubscriptionsStore =
            object : TvSubscriptionsStore {
                private val subscriptions
                    get() = StateSubscriptions.instance

                private val settings
                    get() = FragmentedStorage.get<FeedFilterSettings>("subFeedFilter")

                override fun channels() =
                    subscriptions.getSubscriptions().sortedByDescending { it.playbackSeconds }.map { it.channel }

                override fun filters() = settings.let {
                    TvSubscriptionFilters(
                        ContentType.MEDIA in it.allowContentTypes, it.allowLive, it.allowPlanned, it.allowWatched)
                }

                override fun saveFilters(filters: TvSubscriptionFilters) {
                    settings.apply {
                        allowContentTypes.removeAll { it == ContentType.MEDIA || it == ContentType.NESTED_VIDEO }
                        if (filters.videos)
                            allowContentTypes.addAll(listOf(ContentType.MEDIA, ContentType.NESTED_VIDEO))
                        allowLive = filters.live
                        allowPlanned = filters.planned
                        allowWatched = filters.watched
                        save()
                    }
                }

                override fun watched(video: IPlatformVideo) =
                    StateHistory.instance.isHistoryWatched(video.url, video.duration)

                override fun hasEnabledSources() =
                    channels().any { StatePlatform.instance.hasEnabledChannelClient(it.url) }

                override fun refreshOnOpen(changed: Boolean) =
                    Settings.instance.subscriptions.fetchOnTabOpen &&
                        (changed ||
                            subscriptions.global.feed == null ||
                            subscriptions.getOldestUpdateTime().isBefore(OffsetDateTime.now().minusMinutes(5)))

                override suspend fun cached(): IPager<IPlatformContent> =
                    StateCache.instance.getSubscriptionCachePager()

                override suspend fun refresh(bypassRateLimit: Boolean): TvSubscriptionFeed {
                    val feed = subscriptions.global
                    if (!bypassRateLimit && !feed.isGlobalUpdating) {
                        val limited = subscriptions.getSubscriptionRequestCount()
                            .filter { (client, count) ->
                                client.getSubscriptionRateLimit()?.let { count > it } == true
                            }
                            .keys
                            .map { it.config.name }
                        if (limited.isNotEmpty()) throw TvSubscriptionRateLimit(limited)
                    }
                    val pager = suspendCancellableCoroutine<IPager<IPlatformContent>> { continuation ->
                        val listener = Any()
                        synchronized(feed.lock) {
                            feed.onUpdatedOnce.subscribe(listener) { error ->
                                if (continuation.isActive) {
                                    if (error != null) continuation.resumeWithException(error)
                                    else
                                        feed.feed?.let { continuation.resume(it.getWindow()) }
                                            ?: continuation.resumeWithException(
                                                IllegalStateException("No subscription feed"))
                                }
                            }
                            continuation.invokeOnCancellation { feed.onUpdatedOnce.remove(listener) }
                            if (!feed.isGlobalUpdating) feed.exceptions = emptyList()
                            subscriptions.updateSubscriptionFeed(StateApp.instance.scope)
                        }
                    }
                    return TvSubscriptionFeed(
                        if (Settings.instance.subscriptions.alwaysReloadFromCache) cached() else pager,
                        feed.exceptions.size)
                }

                override fun observe(changed: () -> Unit, progress: (Int, Int) -> Unit): () -> Unit {
                    val listener = Any()
                    subscriptions.onSubscriptionsChanged.subscribe(listener) { _, _ -> changed() }
                    subscriptions.onFeedProgress.subscribe(listener) { group, done, total ->
                        if (group == null) progress(done, total)
                    }
                    return {
                        subscriptions.onSubscriptionsChanged.remove(listener)
                        subscriptions.onFeedProgress.remove(listener)
                    }
                }
            }
    }
}
