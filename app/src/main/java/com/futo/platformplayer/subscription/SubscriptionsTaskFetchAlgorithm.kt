package com.futo.platformplayer.subscription

import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.structures.DedupContentPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.api.media.structures.MultiChronoContentPager
import com.futo.platformplayer.cache.ChannelContentCache
import com.futo.platformplayer.engine.exceptions.PluginException
import com.futo.platformplayer.engine.exceptions.ScriptCaptchaRequiredException
import com.futo.platformplayer.engine.exceptions.ScriptCriticalException
import com.futo.platformplayer.exceptions.ChannelException
import com.futo.platformplayer.findNonRuntimeException
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StateSubscriptions
import kotlinx.coroutines.CoroutineScope
import java.lang.IllegalStateException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import kotlin.system.measureTimeMillis

abstract class SubscriptionsTaskFetchAlgorithm(
    scope: CoroutineScope,
    allowFailure: Boolean = false,
    withCacheFallback: Boolean = true,
    _threadPool: ForkJoinPool? = null
) : SubscriptionFetchAlgorithm(scope, allowFailure, withCacheFallback, _threadPool) {


    override fun countRequests(subs: Map<Subscription, List<String>>): Map<JSClient, Int> {
        return getSubscriptionTasks(subs).groupBy { it.client }.toList()
            .associate { Pair(it.first, it.second.filter { !it.fromCache }.size) };
    }

    override fun getSubscriptions(subs: Map<Subscription, List<String>>): Result {
        val tasks = getSubscriptionTasks(subs);

        Logger.i(TAG, "Starting Subscriptions Fetch:\n" +
            "   Tasks: ${tasks.filter { !it.fromCache }.size}\n" +
            "   Cached: ${tasks.filter { it.fromCache }.size}");
        try {
            //TODO: Remove this
            UIDialogs.toast("Tasks: ${tasks.filter { !it.fromCache }.size}\n" +
                "Cached: ${tasks.filter { it.fromCache }.size}", false);
        } catch (ex: Throwable){}

        val exs: ArrayList<Throwable> = arrayListOf();
        val taskResults = arrayListOf<IPager<IPlatformContent>>();

        val forkTasks = mutableListOf<ForkJoinTask<SubscriptionTaskResult>>();
        var finished = 0;
        val exceptionMap: HashMap<Subscription, Throwable> = hashMapOf();
        val concurrency = Settings.instance.subscriptions.getSubscriptionsConcurrency();
        val failedPlugins = arrayListOf<String>();
        val cachedChannels = arrayListOf<String>();

        for(task in tasks) {
            val forkTask = threadPool.submit<SubscriptionTaskResult> {
                synchronized(cachedChannels) {
                    if(task.fromCache) {
                        finished++;
                        onProgress.emit(finished, forkTasks.size);
                        if(cachedChannels.contains(task.url))
                            return@submit SubscriptionTaskResult(task, null, null);
                        else {
                            cachedChannels.add(task.url);
                            return@submit SubscriptionTaskResult(task, ChannelContentCache.instance.getChannelCachePager(task.url), null);
                        }
                    }
                }

                val shouldIgnore = synchronized(failedPlugins) { failedPlugins.contains(task.client.id) };
                if(shouldIgnore)
                    return@submit SubscriptionTaskResult(task, null, null); //skipped

                var taskEx: Throwable? = null;
                var pager: IPager<IPlatformContent>;
                try {
                    val time = measureTimeMillis {
                        pager = StatePlatform.instance.getChannelContent(task.client,
                            task.url, task.type, ResultCapabilities.ORDER_CHONOLOGICAL);

                        pager = ChannelContentCache.cachePagerResults(scope, pager) {
                            onNewCacheHit.emit(task.sub, it);
                        };

                        val initialPage = pager.getResults();
                        task.sub.updateSubscriptionState(task.type, initialPage);
                        StateSubscriptions.instance.saveSubscription(task.sub);

                        finished++;
                        onProgress.emit(finished, forkTasks.size);
                    }
                    Logger.i("StateSubscriptions", "Subscription [${task.sub.channel.name}]:${task.type} results in ${time}ms");
                    return@submit SubscriptionTaskResult(task, pager, null);
                } catch (ex: Throwable) {
                    Logger.e(StateSubscriptions.TAG, "Subscription [${task.sub.channel.name}] failed", ex);
                    val channelEx = ChannelException(task.sub.channel, ex);
                    finished++;
                    onProgress.emit(finished, forkTasks.size);
                    if (!withCacheFallback)
                        throw channelEx;
                    else {
                        Logger.i(StateSubscriptions.TAG, "Channel ${task.sub.channel.name} failed, substituting with cache");
                        pager = ChannelContentCache.instance.getChannelCachePager(task.sub.channel.url);
                        taskEx = ex;
                    }
                }
                return@submit SubscriptionTaskResult(task, null, taskEx);
            }
            forkTasks.add(forkTask);
        }

        val timeTotal = measureTimeMillis {
            for(task in forkTasks) {
                try {
                    val result = task.get();
                    if(result != null) {
                        if(result.pager != null)
                            taskResults.add(result.pager!!);
                        if(exceptionMap.containsKey(result.task.sub)) {
                            val ex = exceptionMap[result.task.sub];
                            if(ex != null) {
                                val nonRuntimeEx = findNonRuntimeException(ex);
                                if (nonRuntimeEx != null && (nonRuntimeEx is PluginException || nonRuntimeEx is ChannelException))
                                    exs.add(nonRuntimeEx);
                                else
                                    throw ex.cause ?: ex;
                            }
                        }
                    }
                } catch (ex: ExecutionException) {
                    val nonRuntimeEx = findNonRuntimeException(ex.cause);
                    if(nonRuntimeEx != null && (nonRuntimeEx is PluginException || nonRuntimeEx is ChannelException))
                        exs.add(nonRuntimeEx);
                    else
                        throw ex.cause ?: ex;
                };
            }
        }
        Logger.i("StateSubscriptions", "Subscriptions results in ${timeTotal}ms")
        val pager = MultiChronoContentPager(taskResults, allowFailure, 15);
        pager.initialize();

        return Result(DedupContentPager(pager), exs);
    }

    abstract fun getSubscriptionTasks(subs: Map<Subscription, List<String>>): List<SubscriptionTask>;


    class SubscriptionTask(
        val client: JSClient,
        val sub: Subscription,
        val url: String,
        val type: String,
        var fromCache: Boolean = false
    );

    class SubscriptionTaskResult(
        val task: SubscriptionTask,
        val pager: IPager<IPlatformContent>?,
        val exception: Throwable?
    )
}