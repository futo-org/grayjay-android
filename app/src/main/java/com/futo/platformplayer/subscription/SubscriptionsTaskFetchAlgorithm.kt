package com.futo.platformplayer.subscription

import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.structures.DedupContentPager
import com.futo.platformplayer.api.media.structures.EmptyPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.api.media.structures.MultiChronoContentPager
import com.futo.platformplayer.engine.exceptions.PluginException
import com.futo.platformplayer.engine.exceptions.ScriptCaptchaRequiredException
import com.futo.platformplayer.engine.exceptions.ScriptCriticalException
import com.futo.platformplayer.engine.exceptions.ScriptException
import com.futo.platformplayer.exceptions.ChannelException
import com.futo.platformplayer.findNonRuntimeException
import com.futo.platformplayer.fragment.mainactivity.main.SubscriptionsFeedFragment
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateCache
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.states.StateSubscriptions
import kotlinx.coroutines.CoroutineScope
import java.time.OffsetDateTime
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

        val tasksGrouped = tasks.groupBy { it.client }

        Logger.i(TAG, "Starting Subscriptions Fetch:\n" +
            tasksGrouped.map { "   ${it.key.name}: ${it.value.count { !it.fromCache }}, Cached(${it.value.count { it.fromCache } - it.value.count { it.fromPeek && it.fromCache }}), Peek(${it.value.count { it.fromPeek }})" }.joinToString("\n"));

        try {
            for(clientTasks in tasksGrouped) {
                val clientTaskCount = clientTasks.value.count { !it.fromCache };
                val clientCacheCount = clientTasks.value.count { it.fromCache && !it.fromPeek };
                val clientPeekCount = clientTasks.value.count { it.fromPeek };
                val limit = clientTasks.key.getSubscriptionRateLimit();
                if(clientCacheCount > 0 && clientTaskCount > 0 && limit != null && clientTaskCount >= limit && StateApp.instance.contextOrNull?.let { it is MainActivity && it.isFragmentActive<SubscriptionsFeedFragment>() } == true) {
                    UIDialogs.appToast("[${clientTasks.key.name}] only updating ${clientTaskCount} most urgent channels (rqs). " +
                            "(${if(clientPeekCount > 0) "${clientPeekCount} peek, " else ""}${clientCacheCount} cached)");
                }
            }

        } catch (e: Throwable){
            Logger.e(TAG, "Error occurred in task.", e)
        }

        val exs: ArrayList<Throwable> = arrayListOf();

        val failedPlugins = mutableListOf<String>();
        val cachedChannels = mutableListOf<String>()
        val forkTasks = executeSubscriptionTasks(tasks, failedPlugins, cachedChannels);

        val taskResults = arrayListOf<SubscriptionTaskResult>();
        val timeTotal = measureTimeMillis {
            for(task in forkTasks) {
                try {
                    val result = task.get();
                    if(result != null) {
                        if(result.pager != null) {
                            taskResults.add(result);
                        }

                        if(result.exception != null) {
                            val ex = result.exception;
                            val nonRuntimeEx = findNonRuntimeException(ex);
                            if (nonRuntimeEx != null && (nonRuntimeEx is PluginException || nonRuntimeEx is ChannelException)) {
                                exs.add(nonRuntimeEx);
                            } else {
                                throw ex.cause ?: ex;
                            }
                        }
                    }
                } catch (ex: ExecutionException) {
                    val nonRuntimeEx = findNonRuntimeException(ex.cause);
                    if(nonRuntimeEx != null && (nonRuntimeEx is PluginException || nonRuntimeEx is ChannelException)) {
                        exs.add(nonRuntimeEx);
                    } else {
                        throw ex.cause ?: ex;
                    }
                };
            }
        }
        Logger.i("StateSubscriptions", "Subscriptions results in ${timeTotal}ms")

        //Cache pagers grouped by channel
        val groupedPagers = taskResults.groupBy { it.task.sub.channel.url }
            .map { entry ->
                val sub = if(!entry.value.isEmpty()) entry.value[0].task.sub else null;
                val liveTasks = entry.value.filter { !it.task.fromCache };
                val cachedTasks = entry.value.filter { it.task.fromCache };
                val livePager = if(liveTasks.isNotEmpty()) StateCache.cachePagerResults(scope, MultiChronoContentPager(liveTasks.map { it.pager!! }, true).apply { this.initialize() }) {
                    onNewCacheHit.emit(sub!!, it);
                } else null;
                val cachedPager = if(cachedTasks.isNotEmpty()) MultiChronoContentPager(cachedTasks.map { it.pager!! }, true).apply { this.initialize() } else null;
                if(livePager != null && cachedPager == null) {
                    return@map livePager;
                } else if(cachedPager != null && livePager == null) {
                    return@map cachedPager;
                } else if(cachedPager == null) {
                    return@map EmptyPager();
                } else {
                    return@map MultiChronoContentPager(listOf(livePager!!, cachedPager), true).apply { this.initialize() }
                }
            }

        val pager = MultiChronoContentPager(groupedPagers, allowFailure, 15);
        pager.initialize();

        return Result(DedupContentPager(pager, StatePlatform.instance.getEnabledClients().map { it.id }), exs);
    }

    fun executeSubscriptionTasks(tasks: List<SubscriptionTask>, failedPlugins: MutableList<String>, cachedChannels: MutableList<String>): List<ForkJoinTask<SubscriptionTaskResult>> {
        val forkTasks = mutableListOf<ForkJoinTask<SubscriptionTaskResult>>();
        var finished = 0;

        for(task in tasks) {
            val forkTask = threadPool.submit<SubscriptionTaskResult> {
                if(StatePlugins.instance.isUpdating(task.client.id)){
                    val isUpdatingException = ScriptCriticalException(task.client.config, "Plugin is updating");
                    synchronized(failedPlugins) {
                        //Fail all subscription calls to plugin if it has a critical issue
                        if(isUpdatingException.config is SourcePluginConfig && !failedPlugins.contains(isUpdatingException.config.id)) {
                            Logger.w(StateSubscriptions.TAG, "Subscriptions ignoring plugin [${isUpdatingException.config.name}] due to critical exception:\n" + isUpdatingException.message);
                            failedPlugins.add(isUpdatingException.config.id);
                        }
                    }
                    return@submit SubscriptionTaskResult(task, StateCache.instance.getChannelCachePager(task.sub.channel.url), isUpdatingException);
                }

                if(task.fromPeek) {
                    try {

                        val time = measureTimeMillis {
                            val peekResults = StatePlatform.instance.peekChannelContents(task.client, task.url, task.type);
                            val mostRecent = peekResults.firstOrNull();
                            task.sub.lastPeekVideo = mostRecent?.datetime ?: OffsetDateTime.MIN;
                            task.sub.saveAsync();
                            val cacheItems = peekResults.filter { it.datetime != null && it.datetime!! > task.sub.lastVideoUpdate };
                            //Fix for current situation
                            for(item in cacheItems) {
                                if(item.author.thumbnail.isNullOrEmpty())
                                    item.author.thumbnail = task.sub.channel.thumbnail;
                            }
                            StateCache.instance.cacheContents(cacheItems, false);
                        }
                        Logger.i("StateSubscriptions", "Subscription peek [${task.sub.channel.name}]:${task.type} results in ${time}ms");
                    }
                    catch(ex: Throwable) {
                        Logger.e(StateSubscriptions.TAG, "Subscription peek [${task.sub.channel.name}] failed", ex);
                    }
                }
                synchronized(cachedChannels) {
                    if(task.fromCache || task.fromPeek) {
                        finished++;
                        onProgress.emit(finished, forkTasks.size);
                        if(cachedChannels.contains(task.url)) {
                            return@submit SubscriptionTaskResult(task, null, null);
                        } else {
                            cachedChannels.add(task.url);
                            return@submit SubscriptionTaskResult(task, StateCache.instance.getChannelCachePager(task.url), null);
                        }
                    }
                }

                val shouldIgnore = synchronized(failedPlugins) { failedPlugins.contains(task.client.id) };
                if(shouldIgnore) {
                    return@submit SubscriptionTaskResult(task, null, null); //skipped
                }

                val taskEx: Throwable?;
                var pager: IPager<IPlatformContent>;
                try {
                    val time = measureTimeMillis {
                        pager = StatePlatform.instance.getChannelContent(task.client,
                            task.url, task.type, ResultCapabilities.ORDER_CHONOLOGICAL);

                        val initialPage = pager.getResults();
                        task.sub.updateSubscriptionState(task.type, initialPage);
                        task.sub.save();

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


                    if(ex is ScriptCaptchaRequiredException) {
                        synchronized(failedPlugins) {
                            //Fail all subscription calls to plugin if it has a captcha issue
                            if(ex.config is SourcePluginConfig && !failedPlugins.contains(ex.config.id)) {
                                Logger.w(StateSubscriptions.TAG, "Subscriptions ignoring plugin [${ex.config.name}] due to Captcha");
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

                    if (!withCacheFallback)
                        throw channelEx;
                    else {
                        Logger.i(StateSubscriptions.TAG, "Channel ${task.sub.channel.name} failed, substituting with cache");
                        pager = StateCache.instance.getChannelCachePager(task.sub.channel.url);
                        taskEx = channelEx;
                        return@submit SubscriptionTaskResult(task, pager, taskEx);
                    }
                }
            }
            forkTasks.add(forkTask);
        }
        return forkTasks;
    }

    abstract fun getSubscriptionTasks(subs: Map<Subscription, List<String>>): List<SubscriptionTask>;

    class SubscriptionTask(
        val client: JSClient,
        val sub: Subscription,
        val url: String,
        val type: String,
        var fromCache: Boolean = false,
        var fromPeek: Boolean = false,
        var urgency: Int = 0
    );

    class SubscriptionTaskResult(
        val task: SubscriptionTask,
        val pager: IPager<IPlatformContent>?,
        val exception: Throwable?
    )
}