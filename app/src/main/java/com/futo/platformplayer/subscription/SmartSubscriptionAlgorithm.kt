package com.futo.platformplayer.subscription

import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.getNowDiffDays
import com.futo.platformplayer.getNowDiffHours
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.states.StatePlatform
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ForkJoinPool

class SmartSubscriptionAlgorithm(
    scope: CoroutineScope,
    allowFailure: Boolean = false,
    withCacheFallback: Boolean = true,
    threadPool: ForkJoinPool? = null
): SubscriptionsTaskFetchAlgorithm(scope, allowFailure, withCacheFallback, threadPool) {
    override fun getSubscriptionTasks(subs: Map<Subscription, List<String>>): List<SubscriptionTask> {
        val allTasks: List<SubscriptionTask> = subs.flatMap { entry ->
            val sub = entry.key;
            //Get all urls associated with this subscriptions
            val allPlatforms = entry.value.associateWith { StatePlatform.instance.getChannelClientOrNull(it) }
                .filterValues { it is JSClient };

            //For every platform, get all sub-queries associated with that platform
            return@flatMap allPlatforms
                .filter { it.value != null }
                .flatMap {
                    val url = it.key;
                    val client = it.value!! as JSClient;
                    val capabilities = client.getChannelCapabilities();

                    if(capabilities.hasType(ResultCapabilities.TYPE_MIXED))
                        return@flatMap listOf(SubscriptionTask(client, sub, it.key, ResultCapabilities.TYPE_MIXED));
                    else {
                        val types = listOf(
                              if(sub.shouldFetchVideos()) ResultCapabilities.TYPE_VIDEOS else null,
                              if(sub.shouldFetchStreams()) ResultCapabilities.TYPE_STREAMS else null,
                              if(sub.shouldFetchPosts()) ResultCapabilities.TYPE_POSTS else null,
                              if(sub.shouldFetchLiveStreams()) ResultCapabilities.TYPE_LIVE else null
                        ).filterNotNull().filter { capabilities.hasType(it) };
                        return@flatMap types.map {
                            SubscriptionTask(client, sub, url, it);
                        };
                    }
                };
        };

        for(task in allTasks)
            task.urgency = calculateUpdateUrgency(task.sub, task.type);

        val ordering = allTasks.groupBy { it.client }
            .map { Pair(it.key, it.value.sortedBy { it.urgency }) };

        val finalTasks = mutableListOf<SubscriptionTask>();


        for(clientTasks in ordering) {
            val limit = clientTasks.first.config.subscriptionRateLimit;
            if(limit == null || limit <= 0)
                finalTasks.addAll(clientTasks.second);
            else {
                val fetchTasks = clientTasks.second.take(limit);
                val cacheTasks = clientTasks.second.drop(limit);

                for(cacheTask in cacheTasks)
                    cacheTask.fromCache = true;

                Logger.i(TAG, "Subscription Client Budget [${clientTasks.first.name}]: ${fetchTasks.size}/${limit}")

                finalTasks.addAll(fetchTasks + cacheTasks);
            }
        }

        return finalTasks;
    }


    fun calculateUpdateUrgency(sub: Subscription, type: String): Int {
        val lastItem = when(type) {
            ResultCapabilities.TYPE_VIDEOS -> sub.lastVideo;
            ResultCapabilities.TYPE_STREAMS -> sub.lastLiveStream;
            ResultCapabilities.TYPE_LIVE -> sub.lastLiveStream;
            ResultCapabilities.TYPE_POSTS -> sub.lastPost;
            else -> sub.lastVideo; //TODO: minimum of all?
        };
        val lastUpdate = when(type) {
            ResultCapabilities.TYPE_VIDEOS -> sub.lastVideoUpdate;
            ResultCapabilities.TYPE_STREAMS -> sub.lastLiveStreamUpdate;
            ResultCapabilities.TYPE_LIVE -> sub.lastLiveStreamUpdate;
            ResultCapabilities.TYPE_POSTS -> sub.lastPostUpdate;
            else -> sub.lastVideoUpdate; //TODO: minimum of all?
        };
        val interval = when(type) {
            ResultCapabilities.TYPE_VIDEOS -> sub.uploadInterval;
            ResultCapabilities.TYPE_STREAMS -> sub.uploadStreamInterval;
            ResultCapabilities.TYPE_LIVE -> sub.uploadStreamInterval;
            ResultCapabilities.TYPE_POSTS -> sub.uploadPostInterval;
            else -> sub.uploadInterval; //TODO: minimum of all?
        };
        val lastItemDaysAgo = lastItem.getNowDiffHours();
        val lastUpdateHoursAgo = lastUpdate.getNowDiffHours();
        val expectedHours = (interval * 24) - lastUpdateHoursAgo.toDouble();

        return (expectedHours * 100).toInt();
    }
}