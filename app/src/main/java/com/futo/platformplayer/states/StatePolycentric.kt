package com.futo.platformplayer.states

import android.content.Context
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.structures.DedupContentPager
import com.futo.platformplayer.api.media.structures.EmptyPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.api.media.structures.MultiChronoContentPager
import com.futo.platformplayer.awaitFirstDeferred
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PolycentricAdapter
import com.futo.platformplayer.resolveChannelUrl
import com.futo.platformplayer.polycentric.getComment
import com.futo.platformplayer.polycentric.getCommentPager
import com.futo.platformplayer.polycentric.deleteComment
import com.futo.platformplayer.polycentric.getLiveVideoRating
import com.futo.platformplayer.polycentric.getMyComments
import com.futo.platformplayer.polycentric.getReplies
import com.futo.platformplayer.polycentric.isMyComment
import com.futo.platformplayer.polycentric.myCommentRating
import com.futo.platformplayer.polycentric.myVideoRating
import com.futo.platformplayer.polycentric.postComment
import com.futo.platformplayer.polycentric.postReply
import com.futo.platformplayer.polycentric.setCommentRating
import com.futo.platformplayer.polycentric.setVideoRating
import com.futo.platformplayer.polycentric.AvatarVariant
import com.futo.platformplayer.polycentric.PolycentricChannelProfile
import com.futo.platformplayer.polycentric.isLoggedIn
import com.futo.platformplayer.polycentric.activeIdentity
import com.futo.platformplayer.polycentric.createIdentity
import com.futo.platformplayer.polycentric.listIdentities
import com.futo.platformplayer.polycentric.switchIdentity
import com.futo.platformplayer.polycentric.getIdentityProfile
import com.futo.platformplayer.polycentric.updateProfile
import com.futo.platformplayer.polycentric.logout
import com.futo.platformplayer.polycentric.deleteActiveIdentity
import com.futo.polycentric.core.PolycentricProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import polycentric.v2.EventKey


class StatePolycentric {
    /**
     * Harbor/Polycentric v2 adapter, which wraps the PolycentricCore provided
		 * by the Kotlin SDK.
		 *
     * Constructed lazily; reads should await the initialization using
		 * [awaitAdapter].
     */
    val adapter: PolycentricAdapter by lazy { PolycentricAdapter() }

    private var _transientEnabled = true
    val enabled get() = _transientEnabled && Settings.instance.other.polycentricEnabled

    private val _backgroundJob = SupervisorJob()
    private val _backgroundScope = CoroutineScope(_backgroundJob + Dispatchers.IO)
    private val _initMutex = Mutex()
    private val _identityMutex = Mutex()

    fun load(context: Context) {
        if (!enabled) {
            return
        }

        _backgroundScope.launch {
            awaitAdapter()
        }
    }

    // ── Multi-identity (Harbor v2) ──────────────────────────────────
    // Stateless wrappers over the adapter's identity extensions; identity
    // management is independent of the comment/rating facade below.
    fun ensureEnabled() {
        if (!enabled) {
            throw IllegalStateException("Harbor is disabled")
        }
    }


    val isLoggedIn: Boolean
        get() = enabled && adapter.isReady && adapter.isLoggedIn

    /**
     * Runs a serialized, non-cancellable identity mutation.
     */
    private suspend fun <T> mutateIdentity(block: suspend (PolycentricAdapter) -> T): T {
        ensureEnabled()
        val adapter = awaitAdapter() ?: throw IllegalStateException("Harbor is disabled")
        return withContext(NonCancellable) {
            _identityMutex.withLock { block(adapter) }
        }
    }

    fun activeIdentity(): String? {
        if (!enabled || !adapter.isReady) {
            return null
        }
        return adapter.activeIdentity()
    }

    suspend fun createIdentity(name: String): String = mutateIdentity { it.createIdentity(name) }

    suspend fun listIdentities(): List<String> {
        ensureEnabled()
        awaitAdapter() ?: return listOf()
        return adapter.listIdentities()
    }

    suspend fun switchIdentity(identity: String) = mutateIdentity { it.switchIdentity(identity) }

    suspend fun getIdentityProfile(identity: String): PolycentricChannelProfile {
        ensureEnabled()
        val adapter = awaitAdapter() ?: throw IllegalStateException("Harbor is disabled")
        return adapter.getIdentityProfile(identity)
    }

    suspend fun updateProfile(name: String, avatar: List<AvatarVariant>?, mimeType: String = "image/png") =
        mutateIdentity { it.updateProfile(name, avatar, mimeType) }

    suspend fun logout() = mutateIdentity { it.logout() }

    suspend fun deleteIdentity() = mutateIdentity { it.deleteActiveIdentity() }


    fun getChannelUrls(url: String, channelId: PlatformID? = null, cacheOnly: Boolean = false, doCacheNull: Boolean = false): List<String> {
        return getChannelUrlsWithUpdateResult(url, channelId, cacheOnly, doCacheNull).second;
    }
    fun getChannelUrlsWithUpdateResult(url: String, channelId: PlatformID? = null, cacheOnly: Boolean = false, doCacheNull: Boolean = false): Pair<Boolean, List<String>> {
        var didUpdate = false;
        if (!enabled) {
            return Pair(false, listOf(url));
        }
        return Pair(didUpdate, listOf(url));
    }

    fun getChannelContent(scope: CoroutineScope, profile: PolycentricProfile, isSubscriptionOptimized: Boolean = false, channelConcurrency: Int = -1, type: String? = null): IPager<IPlatformContent>? {
        ensureEnabled()

        //TODO: Currently abusing subscription concurrency for parallelism
        val concurrency = if (channelConcurrency == -1) Settings.instance.subscriptions.getSubscriptionsConcurrency() else channelConcurrency;
        val deferred = profile.ownedClaims.groupBy { it.claim.claimType }
            .mapNotNull {
                val url = it.value.firstOrNull()?.claim?.resolveChannelUrl() ?: return@mapNotNull null;
                val client = StatePlatform.instance.getChannelClientOrNull(url) ?: return@mapNotNull null;

                return@mapNotNull Pair(client, scope.async(Dispatchers.IO) {
                    try {
                        if (type == null) {
                            return@async StatePlatform.instance.getChannelContent(url, isSubscriptionOptimized, concurrency);
                        } else {
                            return@async StatePlatform.instance.getChannelContent(url, isSubscriptionOptimized, concurrency, type = type);
                        }
                    } catch (ex: Throwable) {
                        Logger.e(TAG, "getChannelContent", ex);
                        return@async null;
                    }
                })
            }
            .groupBy { it.first.name }
            .map { it.value.first() };
        val finishedPager: Pair<Deferred<IPager<IPlatformContent>?>, IPager<IPlatformContent>?> = (if(deferred.isEmpty()) null else runBlocking {
                deferred.map { it.second }.awaitFirstDeferred();
            }) ?: return null;

        val toAwait = deferred.filter { it.second != finishedPager.first };

        //TODO: Get a Parallel pager to work here.
        val innerPager = MultiChronoContentPager(listOf(finishedPager.second!!) + toAwait.mapNotNull { runBlocking { it.second.await(); } });
        innerPager.initialize();
        //return RefreshChronoContentPager(listOf(finishedPager.second!!), toAwait.map { it.second }, listOf());
        //return RefreshDedupContentPager(RefreshChronoContentPager(listOf(finishedPager.second!!), toAwait.map { it.second }, listOf()), StatePlatform.instance.getEnabledClients().map { it.id });
        return DedupContentPager(innerPager, StatePlatform.instance.getEnabledClients().map { it.id });

    /* //Gives out-of-order results
        return RefreshDedupContentPager(RefreshDistributionContentPager(
            listOf(finishedPager.second!!),
            toAwait.map { it.second },
            toAwait.map { PlaceholderPager(5) { PlatformContentPlaceholder(it.first.id) } }),
            StatePlatform.instance.getEnabledClients().map { it.id }
        );*/
    }

    /**
     * Returns the Harbor adapter once its client is initialized, or null when
     * Polycentric is disabled or initialization failed.
     */
    suspend fun awaitAdapter(): PolycentricAdapter? {
        if (!enabled) {
            return null
        }

        return try {
            requireAdapter()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.e(TAG, "Polycentric adapter not available.", e)
            null
        }
    }

    /**
     * Returns the Harbor adapter once its client is initialized, throwing when
     * Polycentric is disabled or with the cause when initialization failed.
     */
    suspend fun requireAdapter(): PolycentricAdapter {
        ensureEnabled()
        _initMutex.withLock {
            if (!adapter.isReady) {
                adapter.initialize()
            }
        }
        return adapter
    }

    suspend fun getCommentPager(videoUrl: String): IPager<IPlatformComment> {
        val adapter = awaitAdapter() ?: return EmptyPager()
        return try {
            adapter.getCommentPager(videoUrl)
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to load comments.", e)
            EmptyPager()
        }
    }

    suspend fun getReplies(comment: PolycentricPlatformComment): IPager<IPlatformComment> {
        val adapter = awaitAdapter() ?: return EmptyPager()
        return try {
            adapter.getReplies(comment)
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to load replies.", e)
            EmptyPager()
        }
    }

    suspend fun getComment(key: EventKey): PolycentricPlatformComment? {
        val adapter = awaitAdapter() ?: return null
        return try {
            adapter.getComment(key)
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to load comment.", e)
            null
        }
    }

    suspend fun getMyComments(): List<IPlatformComment> {
        val adapter = awaitAdapter() ?: return listOf()
        return try {
            adapter.getMyComments()
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to load my comments.", e)
            listOf()
        }
    }

    suspend fun getLiveVideoRating(videoUrl: String): RatingLikeDislikes {
        val adapter = awaitAdapter() ?: return RatingLikeDislikes(0, 0)
        return try {
            adapter.getLiveVideoRating(videoUrl)
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to load video rating.", e)
            RatingLikeDislikes(0, 0)
        }
    }

    /** Whether the active Harbor identity has liked (true) or disliked (false) a video. */
    fun myVideoRating(videoUrl: String): Boolean? {
        if (!enabled) {
            return null
        }
        return try {
            adapter.myVideoRating(videoUrl)
        } catch (e: Throwable) {
            null
        }
    }

    /** Whether the active Harbor identity has liked (true) or disliked (false) a comment. */
    fun myCommentRating(comment: PolycentricPlatformComment): Boolean? {
        if (!enabled) {
            return null
        }
        return try {
            adapter.myCommentRating(comment)
        } catch (e: Throwable) {
            null
        }
    }

    /** Whether the comment was authored by the active Harbor identity (deletable). */
    fun isMyComment(comment: PolycentricPlatformComment): Boolean {
        if (!enabled) {
            return false
        }
        return try {
            adapter.isMyComment(comment)
        } catch (e: Throwable) {
            false
        }
    }

    // Writes delegate to the adapter; they throw when no Harbor identity is
    // available (the v2 identity system is not wired up yet).
    suspend fun postComment(videoUrl: String, videoTitle: String?, text: String): PolycentricPlatformComment {
        awaitAdapter() ?: throw IllegalStateException("Harbor is disabled")
        return adapter.postComment(videoUrl, videoTitle, text)
    }

    suspend fun postReply(parent: PolycentricPlatformComment, text: String): PolycentricPlatformComment {
        awaitAdapter() ?: throw IllegalStateException("Harbor is disabled")
        return adapter.postReply(parent, text)
    }

    suspend fun deleteComment(comment: PolycentricPlatformComment) {
        awaitAdapter() ?: throw IllegalStateException("Harbor is disabled")
        adapter.deleteComment(comment)
    }

    suspend fun setVideoRating(videoUrl: String, videoTitle: String?, positive: Boolean?) {
        awaitAdapter() ?: throw IllegalStateException("Harbor is disabled")
        adapter.setVideoRating(videoUrl, videoTitle, positive)
    }

    suspend fun setCommentRating(comment: PolycentricPlatformComment, positive: Boolean?) {
        awaitAdapter() ?: throw IllegalStateException("Harbor is disabled")
        adapter.setCommentRating(comment, positive)
    }

    fun cleanup() {
        _backgroundJob.cancel()
    }

    companion object {
        private const val TAG = "StatePolycentric";

        private var _instance: StatePolycentric? = null;
        val instance: StatePolycentric
            @Synchronized get(){
                if(_instance == null)
                    _instance = StatePolycentric();
                return _instance!!;
            };
        /** Adapter singleton teardown. */
        @Synchronized
        fun dispose() {
            _instance?.cleanup()
            _instance = null;
        }
    }
}
