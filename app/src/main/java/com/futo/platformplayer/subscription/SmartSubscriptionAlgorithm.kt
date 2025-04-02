package com.futo.platformplayer.subscription

import SubsExchangeClient
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.getNowDiffHours
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.states.StatePlatform
import kotlinx.coroutines.CoroutineScope
import java.time.OffsetDateTime
import java.util.concurrent.ForkJoinPool

class SmartSubscriptionAlgorithm(
    scope: CoroutineScope,
    allowFailure: Boolean = false,
    withCacheFallback: Boolean = true,
    threadPool: ForkJoinPool? = null,
    subsExchangeClient: SubsExchangeClient? = null
): SubscriptionsTaskFetchAlgorithm(scope, allowFailure, withCacheFallback, threadPool, subsExchangeClient) {
    override fun getSubscriptionTasks(subs: Map<Subscription, List<String>>): List<SubscriptionTask> {
        val allTasks: List<SubscriptionTask> = subs.flatMap { entry ->
            val sub = entry.key;
            //Get all urls associated with this subscriptions
            val allPlatforms = entry.value.associateWith { StatePlatform.instance.getChannelClientOrNull(it) }
                .filterValues { it is JSClient };

            //For every platform, get all sub-queries associated with that platform
            return@flatMap allPlatforms
                .filter { it.value != null }
                .flatMap innerFlatMap@ { pair ->
                    val url = pair.key;
                    val client = pair.value!! as JSClient;
                    val capabilities = client.getChannelCapabilities();

                    if(capabilities.hasType(ResultCapabilities.TYPE_MIXED) || capabilities.types.isEmpty())
                        return@innerFlatMap listOf(SubscriptionTask(client, sub, pair.key, ResultCapabilities.TYPE_MIXED));
                    else if(capabilities.hasType(ResultCapabilities.TYPE_SUBSCRIPTIONS))
                        return@innerFlatMap listOf(SubscriptionTask(client, sub, pair.key, ResultCapabilities.TYPE_SUBSCRIPTIONS))
                    else {
                        val types = listOfNotNull(
                            if (sub.shouldFetchVideos()) ResultCapabilities.TYPE_VIDEOS else null,
                            if (sub.shouldFetchStreams()) ResultCapabilities.TYPE_STREAMS else null,
                            if (sub.shouldFetchPosts()) ResultCapabilities.TYPE_POSTS else null,
                            if (sub.shouldFetchLiveStreams()) ResultCapabilities.TYPE_LIVE else null
                        ).filter { capabilities.hasType(it) };

                        if(types.isNotEmpty()) {
                            return@innerFlatMap types.map {
                                SubscriptionTask(client, sub, url, it);
                            };
                        } else {
                            listOf(SubscriptionTask(client, sub, url, ResultCapabilities.TYPE_VIDEOS, true))
                        }
                    }
                };
        };

        for(task in allTasks) {
            task.urgency = calculateUpdateUrgency(task.sub, task.type);
        }

        val ordering = allTasks.groupBy { it.client }
            .map { Pair(it.key, it.value.sortedBy { it.urgency }) };

        val finalTasks = mutableListOf<SubscriptionTask>();


        for(clientTasks in ordering) {
            val limit = clientTasks.first.getSubscriptionRateLimit();
            if(limit == null || limit <= 0) {
                finalTasks.addAll(clientTasks.second);
            } else {
                val fetchTasks = mutableListOf<SubscriptionTask>();
                val cacheTasks = mutableListOf<SubscriptionTask>();
                var peekTasks = mutableListOf<SubscriptionTask>();

                for(task in clientTasks.second) {
                    if (!task.fromCache && fetchTasks.size < limit) {
                        fetchTasks.add(task);
                    } else {
                        if(peekTasks.size < 100 &&
                                Settings.instance.subscriptions.peekChannelContents &&
                                (task.sub.lastPeekVideo.year < 1971 || task.sub.lastPeekVideo < task.sub.lastVideoUpdate) &&
                                task.client.capabilities.hasPeekChannelContents &&
                                task.client.getPeekChannelTypes().contains(task.type)) {
                            task.fromPeek = true;
                            task.fromCache = true;
                            peekTasks.add(task);
                        }
                        else {
                            task.fromCache = true;
                            cacheTasks.add(task);
                        }
                    }
                }
                Logger.i(TAG, "Subscription Client Budget [${clientTasks.first.name}]: ${fetchTasks.size}/${limit}")

                finalTasks.addAll(fetchTasks + peekTasks + cacheTasks);
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

        if((type == ResultCapabilities.TYPE_MIXED || type == ResultCapabilities.TYPE_VIDEOS) && (sub.lastPeekVideo.year > 1970 && sub.lastPeekVideo > sub.lastVideoUpdate))
            return 0;
        else
            return (expectedHours * 100).toInt();
    }
}