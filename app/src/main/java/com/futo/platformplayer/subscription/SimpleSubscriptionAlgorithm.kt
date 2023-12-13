package com.futo.platformplayer.subscription

import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.structures.DedupContentPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.api.media.structures.MultiChronoContentPager
import com.futo.platformplayer.engine.exceptions.PluginException
import com.futo.platformplayer.engine.exceptions.ScriptCaptchaRequiredException
import com.futo.platformplayer.engine.exceptions.ScriptCriticalException
import com.futo.platformplayer.exceptions.ChannelException
import com.futo.platformplayer.findNonRuntimeException
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.states.StateCache
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StateSubscriptions
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ExecutionException
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import kotlin.system.measureTimeMillis

class SimpleSubscriptionAlgorithm(
    scope: CoroutineScope,
    allowFailure: Boolean = false,
    withCacheFallback: Boolean = true,
    threadPool: ForkJoinPool? = null
): SubscriptionFetchAlgorithm(scope, allowFailure, withCacheFallback, threadPool) {

    override fun countRequests(subs: Map<Subscription, List<String>>): Map<JSClient, Int> {
        val pluginReqCounts = mutableMapOf<JSClient, Int>();

        for(sub in subs) {
            for(subUrl in sub.value) {
                val client = StatePlatform.instance.getChannelClientOrNull(sub.key.channel.url);
                if (client !is JSClient) {
                    continue;
                }

                val channelCaps = client.getChannelCapabilities();
                if (!pluginReqCounts.containsKey(client)) {
                    pluginReqCounts[client] = 1;
                } else {
                    pluginReqCounts[client] = pluginReqCounts[client]!! + 1;
                }

                if (channelCaps.hasType(ResultCapabilities.TYPE_STREAMS) && sub.key.shouldFetchStreams())
                    pluginReqCounts[client] = pluginReqCounts[client]!! + 1;
                if (channelCaps.hasType(ResultCapabilities.TYPE_LIVE) && sub.key.shouldFetchLiveStreams())
                    pluginReqCounts[client] = pluginReqCounts[client]!! + 1;
                if (channelCaps.hasType(ResultCapabilities.TYPE_POSTS) && sub.key.shouldFetchPosts())
                    pluginReqCounts[client] = pluginReqCounts[client]!! + 1;
            }
        }
        return pluginReqCounts;
    }

    override fun getSubscriptions(subs: Map<Subscription, List<String>>): Result {
        val subsPager: Array<IPager<IPlatformContent>>;
        val exs: ArrayList<Throwable> = arrayListOf();

        Logger.i(TAG, "getSubscriptions [Simple]");

        val tasks = mutableListOf<ForkJoinTask<Pair<Subscription, IPager<IPlatformContent>?>>>();
        var finished = 0;
        val exceptionMap: HashMap<Subscription, Throwable> = hashMapOf();
        val failedPlugins = arrayListOf<String>();
        for (sub in subs.filter { StatePlatform.instance.hasEnabledChannelClient(it.key.channel.url) })
            tasks.add(getSubscription(sub.key, sub.value, failedPlugins){ channelEx ->
                finished++;
                onProgress.emit(finished, tasks.size);

                val ex = channelEx?.cause;
                if(channelEx != null) {
                    synchronized(exceptionMap) {
                        exceptionMap.put(sub.key, channelEx);
                    }
                    if(ex is ScriptCaptchaRequiredException) {
                        synchronized(failedPlugins) {
                            //Fail all subscription calls to plugin if it has a captcha issue
                            if(ex.config is SourcePluginConfig && !failedPlugins.contains(ex.config.id)) {
                                Logger.w(StateSubscriptions.TAG, "Subscriptionsgnoring plugin [${ex.config.name}] due to Captcha");
                                failedPlugins.add(ex.config.id);
                            }
                        }
                    }
                    else if(ex is ScriptCriticalException) {
                        synchronized(failedPlugins) {
                            //Fail all subscription calls to plugin if it has a critical issue
                            if(ex.config is SourcePluginConfig && !failedPlugins.contains(ex.config.id)) {
                                Logger.w(StateSubscriptions.TAG, "Subscriptions ignoring plugin [${ex.config.name}] due to critical exception:\n" + ex.message);
                                failedPlugins.add(ex.config.id);
                            }
                        }
                    }
                }
            });

        val timeTotal = measureTimeMillis {
            val taskResults = arrayListOf<IPager<IPlatformContent>>();
            for (task in tasks) {
                try {
                    val result = task.get();
                    if (result != null) {
                        if(result.second != null) {
                            taskResults.add(result.second!!);
                        }

                        if (exceptionMap.containsKey(result.first)) {
                            val ex = exceptionMap[result.first];
                            if (ex != null) {
                                val nonRuntimeEx = findNonRuntimeException(ex);
                                if (nonRuntimeEx != null && (nonRuntimeEx is PluginException || nonRuntimeEx is ChannelException)) {
                                    exs.add(nonRuntimeEx);
                                } else {
                                    throw ex.cause ?: ex;
                                }
                            }
                        }
                    }
                } catch (ex: ExecutionException) {
                    val nonRuntimeEx = findNonRuntimeException(ex.cause);
                    if (nonRuntimeEx != null && (nonRuntimeEx is PluginException || nonRuntimeEx is ChannelException)) {
                        exs.add(nonRuntimeEx);
                    } else {
                        throw ex.cause ?: ex;
                    }
                };
            }
            subsPager = taskResults.toTypedArray();
        }
        Logger.i("StateSubscriptions", "Subscriptions results in ${timeTotal}ms")

        if(subsPager.isEmpty() && exs.any()) {
            throw exs.first();
        }

        Logger.i(StateSubscriptions.TAG, "Subscription pager with ${subsPager.size} channels");
        val pager = MultiChronoContentPager(subsPager, allowFailure, 15);
        pager.initialize();
        return Result(DedupContentPager(pager), exs);
    }

    private fun getSubscription(sub: Subscription, urls: List<String>, failedPlugins: List<String>, onFinished: (ChannelException?)->Unit): ForkJoinTask<Pair<Subscription, IPager<IPlatformContent>?>> {
        return threadPool.submit<Pair<Subscription, IPager<IPlatformContent>?>> {
            val toIgnore = synchronized(failedPlugins){ failedPlugins.toList() };

            var pager: IPager<IPlatformContent>? = null;
            for(url in urls) {
                try {
                    val platformClient = StatePlatform.instance.getChannelClientOrNull(url, toIgnore) ?: continue;
                    val time = measureTimeMillis {
                        pager = StatePlatform.instance.getChannelContent(platformClient, url, true, threadPool.poolSize);
                        pager = StateCache.cachePagerResults(scope, pager!!) {
                            onNewCacheHit.emit(sub, it);
                        };

                        onFinished(null);
                    }
                    Logger.i(
                        "StateSubscriptions",
                        "Subscription [${sub.channel.name}] results in ${time}ms"
                    );
                }
                catch(ex: Throwable) {
                    Logger.e(StateSubscriptions.TAG, "Subscription [${sub.channel.name}] failed", ex);
                    val channelEx = ChannelException(sub.channel, ex);
                    onFinished(channelEx);
                    if(!withCacheFallback) {
                        throw channelEx;
                    } else {
                        Logger.i(StateSubscriptions.TAG, "Channel ${sub.channel.name} failed, substituting with cache");
                        pager = StateCache.instance.getChannelCachePager(sub.channel.url);
                    }
                }
            }

            if(pager == null) {
                throw IllegalStateException("Uncaught nullable pager");
            }

            return@submit Pair(sub, pager);
        };
    }
}