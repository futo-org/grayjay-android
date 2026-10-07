package com.futo.platformplayer.states

import android.content.Context
import android.content.Intent
import android.util.Log
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.PolycentricHomeActivity
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
import com.futo.platformplayer.polycentric.PolycentricStorage
import com.futo.platformplayer.resolveChannelUrl
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringStorage
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
import com.futo.polycentric.core.PolycentricProfile
import com.futo.polycentric.core.ProcessHandle
import com.futo.polycentric.core.PublicKey
import com.futo.polycentric.core.SqlLiteDbHelper
import com.futo.polycentric.core.Store
import com.futo.platformplayer.base64ToByteArray
import com.futo.platformplayer.toBase64
import com.futo.polycentric.core.ensureServerAndBackfill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import polycentric.v2.EventKey
import userpackage.Protocol


class StatePolycentric {
    /**
     * Harbor/Polycentric v2 adapter, which wraps the PolycentricCore provided
		 * by the Kotlin SDK.
		 *
     * Constructed lazily; reads should await the initialization using
		 * [awaitAdapter].
     */
    val adapter: PolycentricAdapter by lazy { PolycentricAdapter() }

    var processHandle: ProcessHandle? = null; private set;
    private val _activeProcessHandle = FragmentedStorage.get<StringStorage>("activeProcessHandle");
    private var _transientEnabled = true
    val enabled get() = _transientEnabled && Settings.instance.other.polycentricEnabled

    private val _backgroundJob = SupervisorJob()
    private val _backgroundScope = CoroutineScope(_backgroundJob + Dispatchers.IO)

    fun load(context: Context) {
        if (!enabled) {
            return
        }

        for (i in 0 .. 1) {
            try {
                val db = SqlLiteDbHelper(context);
                Store.initializeSqlLiteStore(db);

                val activeProcessHandleString = _activeProcessHandle.value;
                if (activeProcessHandleString.isNotEmpty()) {
                    try {
                        val system = PublicKey.fromProto(Protocol.PublicKey.parseFrom(activeProcessHandleString.base64ToByteArray()));
                        setProcessHandle(Store.instance.getProcessSecret(system)?.toProcessHandle());
                    } catch (e: Throwable) {
                        db.upgradeOldSecrets(db.writableDatabase);

                        val system = PublicKey.fromProto(Protocol.PublicKey.parseFrom(activeProcessHandleString.base64ToByteArray()));
                        setProcessHandle(Store.instance.getProcessSecret(system)?.toProcessHandle());

                        Log.i(TAG, "Failed to initialize Polycentric.", e)
                    }
                }

                getProcessHandles()

                // Initialize the Harbor v2 adapter in the background so comment/rating
                // reads only have to await readiness.
                _backgroundScope.launch {
                    try {
                        adapter.initialize()
                        adapter.awaitReady()
                    } catch (e: Throwable) {
                        Logger.w(TAG, "Failed to initialize Polycentric adapter: " + e.message)
                    }
                }

                break;
            } catch (e: Throwable) {
                if (i == 0) {
                    Logger.i(TAG, "Clearing Polycentric database due to corruption");
                    val db = SqlLiteDbHelper(context);
                    db.recreate()
                } else {
                    _transientEnabled = false
                    UIDialogs.showGeneralErrorDialog(context, "Failed to initialize Polycentric.", e);
                    Log.i(TAG, "Failed to initialize Polycentric.", e)
                }
            }
        }
    }

    fun ensureEnabled() {
        if (!enabled) {
            throw Exception("Polycentric is disabled")
        }
    }

    fun getProcessHandles(): List<ProcessHandle> {
        if (!enabled) {
            return listOf()
        }

        val storeProcessSecrets = Store.instance.getProcessSecrets().toMutableList()
        val processSecrets = PolycentricStorage.instance.getProcessSecrets()

        for (processSecret in processSecrets)
        {
            if (!storeProcessSecrets.contains(processSecret)) {
                try {
                    Store.instance.addProcessSecret(processSecret)
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to backfill process secret.")
                }
            }
        }

        for (processSecret in storeProcessSecrets)
        {
            if (!processSecrets.contains(processSecret)) {
                try {
                    PolycentricStorage.instance.addProcessSecret(processSecret)
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to backfill process secret.")
                }
            }
        }

        return (storeProcessSecrets + processSecrets).distinct().map { it.toProcessHandle() }
    }

    fun setProcessHandle(processHandle: ProcessHandle?) {
        ensureEnabled()
        this.processHandle = processHandle;

        if (processHandle != null) {
            _activeProcessHandle.setAndSave(processHandle.system.toProto().toByteArray().toBase64());

            // Ensure current server is registered & synced
            _backgroundScope.launch {
                try {
                    processHandle.ensureServerAndBackfill()
                } catch (e: Throwable) {
                    Logger.w(TAG, "Failed to ensure server and backfill: "+e.message)
                }
            }
        } else {
            _activeProcessHandle.setAndSave("");
        }
    }


    fun requireLogin(context: Context, text: String, action: (processHandle: ProcessHandle) -> Unit) {
        if (!enabled) {
            UIDialogs.toast(context, "Polycentric is disabled")
            return
        }

        val p = processHandle;
        if (p == null) {
            Logger.i(TAG, "requireLogin preventPictureInPicture.emit()");
            StateApp.instance.preventPictureInPicture.emit();
            UIDialogs.showDialog(context, R.drawable.ic_login,
                text, null, null,
                1,
                UIDialogs.Action("Cancel", { }, UIDialogs.ActionStyle.ACCENT),
                UIDialogs.Action("OK", {
                    context.startActivity(Intent(context, PolycentricHomeActivity::class.java));
                }, UIDialogs.ActionStyle.PRIMARY)
            );
        } else {
            action(p);
        }
    }

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
            adapter.initialize()
            adapter.awaitReady()
            adapter
        } catch (e: Throwable) {
            Logger.e(TAG, "Polycentric adapter not available.", e)
            null
        }
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
    suspend fun postComment(videoUrl: String, text: String): PolycentricPlatformComment {
        awaitAdapter() ?: throw IllegalStateException("Polycentric is disabled")
        return adapter.postComment(videoUrl, text)
    }

    suspend fun postReply(parent: PolycentricPlatformComment, text: String): PolycentricPlatformComment {
        awaitAdapter() ?: throw IllegalStateException("Polycentric is disabled")
        return adapter.postReply(parent, text)
    }

    suspend fun deleteComment(comment: PolycentricPlatformComment) {
        awaitAdapter() ?: throw IllegalStateException("Polycentric is disabled")
        adapter.deleteComment(comment)
    }

    suspend fun setVideoRating(videoUrl: String, positive: Boolean?) {
        awaitAdapter() ?: throw IllegalStateException("Polycentric is disabled")
        adapter.setVideoRating(videoUrl, positive)
    }

    suspend fun setCommentRating(comment: PolycentricPlatformComment, positive: Boolean?) {
        awaitAdapter() ?: throw IllegalStateException("Polycentric is disabled")
        adapter.setCommentRating(comment, positive)
    }

    fun cleanup() {
        _backgroundJob.cancel()
    }

    companion object {
        private const val TAG = "StatePolycentric";

        private var _instance: StatePolycentric? = null;
        val instance: StatePolycentric
            get(){
                if(_instance == null)
                    _instance = StatePolycentric();
                return _instance!!;
            };
    }
}
