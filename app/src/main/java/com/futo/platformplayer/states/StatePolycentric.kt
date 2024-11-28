package com.futo.platformplayer.states

import android.content.Context
import android.content.Intent
import android.util.Log
import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.PolycentricHomeActivity
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.LazyComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.structures.DedupContentPager
import com.futo.platformplayer.api.media.structures.EmptyPager
import com.futo.platformplayer.api.media.structures.IAsyncPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.api.media.structures.MultiChronoContentPager
import com.futo.platformplayer.awaitFirstDeferred
import com.futo.platformplayer.dp
import com.futo.platformplayer.fragment.mainactivity.main.PolycentricProfile
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.PolycentricCache
import com.futo.platformplayer.polycentric.PolycentricStorage
import com.futo.platformplayer.resolveChannelUrl
import com.futo.platformplayer.selectBestImage
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringStorage
import com.futo.polycentric.core.ApiMethods
import com.futo.polycentric.core.ClaimType
import com.futo.polycentric.core.ContentType
import com.futo.polycentric.core.Opinion
import com.futo.polycentric.core.ProcessHandle
import com.futo.polycentric.core.PublicKey
import com.futo.polycentric.core.SignedEvent
import com.futo.polycentric.core.SqlLiteDbHelper
import com.futo.polycentric.core.Store
import com.futo.polycentric.core.SystemState
import com.futo.polycentric.core.base64ToByteArray
import com.futo.polycentric.core.systemToURLInfoSystemLinkUrl
import com.futo.polycentric.core.toBase64
import com.futo.polycentric.core.toURLInfoSystemLinkUrl
import com.google.protobuf.ByteString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import userpackage.Protocol
import userpackage.Protocol.Reference
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ForkJoinPool

class StatePolycentric {
    private data class LikeDislikeEntry(val unixMilliseconds: Long, val hasLiked: Boolean, val hasDisliked: Boolean);

    var processHandle: ProcessHandle? = null; private set;
    private var _likeDislikeMap = hashMapOf<String, LikeDislikeEntry>()
    private val _activeProcessHandle = FragmentedStorage.get<StringStorage>("activeProcessHandle");
    private var _transientEnabled = true
    val enabled get() = _transientEnabled && Settings.instance.other.polycentricEnabled

    private val _commentPool = ForkJoinPool(2);
    private val _commentPoolDispatcher = _commentPool.asCoroutineDispatcher();

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

            val newMap = hashMapOf<String, LikeDislikeEntry>()
            Store.instance.enumerateSignedEvents(processHandle.system, ContentType.OPINION) {
                try {
                    for (ref in it.event.references) {
                        val refd = ref.toByteArray().toBase64();
                        val e = newMap[refd];
                        if (e == null || it.event.unixMilliseconds!! > e.unixMilliseconds) {
                            val data = it.event.lwwElement?.value ?: continue;
                            newMap[refd] = LikeDislikeEntry(it.event.unixMilliseconds!!, Opinion(data) == Opinion.like, Opinion(data) == Opinion.dislike);
                        }
                    }
                } catch (e: Throwable) {
                    Logger.w(TAG, "Failed to get opinion, skipped.")
                }
            }

            _likeDislikeMap = newMap
        } else {
            _activeProcessHandle.setAndSave("");
            _likeDislikeMap = hashMapOf()
        }
    }

    fun updateLikeMap(ref: Protocol.Reference, hasLiked: Boolean, hasDisliked: Boolean) {
        ensureEnabled()
        _likeDislikeMap[ref.toByteArray().toBase64()] = LikeDislikeEntry(System.currentTimeMillis(), hasLiked, hasDisliked);
    }

    fun hasDisliked(data: ByteArray): Boolean {
        if (!enabled) {
            return false
        }

        val entry = _likeDislikeMap[data.toBase64()] ?: return false;
        return entry.hasDisliked;
    }

    fun hasLiked(data: ByteArray): Boolean {
        if (!enabled) {
            return false
        }

        val entry = _likeDislikeMap[data.toBase64()] ?: return false;
        return entry.hasLiked;
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
        var polycentricProfile: PolycentricProfile? = null;
        try {
            val polycentricCached = PolycentricCache.instance.getCachedProfile(url, cacheOnly)
            polycentricProfile = polycentricCached?.profile;
            if (polycentricCached == null && channelId != null) {
                Logger.i("StateSubscriptions", "Get polycentric profile not cached");
                if(!cacheOnly) {
                    polycentricProfile = runBlocking { PolycentricCache.instance.getProfileAsync(channelId, if(doCacheNull) url else null) }?.profile;
                    didUpdate = true;
                }
            } else {
                Logger.i("StateSubscriptions", "Get polycentric profile cached");
            }
        }
        catch(ex: Throwable) {
            Logger.w(StateSubscriptions.TAG, "Polycentric getCachedProfile failed for subscriptions", ex);
            //TODO: Some way to communicate polycentric failing without blocking here
        }
        if(polycentricProfile != null) {
            val urls = polycentricProfile.ownedClaims.groupBy { it.claim.claimType }
                .mapNotNull { it.value.firstOrNull()?.claim?.resolveChannelUrl() }.toMutableList();
            if(urls.any { it.equals(url, true) })
                return Pair(didUpdate, urls);
            else
                return Pair(didUpdate, listOf(url) + urls);
        }
        else
            return Pair(didUpdate, listOf(url));
    }

    fun getChannelContent(scope: CoroutineScope, profile: PolycentricProfile, isSubscriptionOptimized: Boolean = false, channelConcurrency: Int = -1): IPager<IPlatformContent>? {
        ensureEnabled()

        //TODO: Currently abusing subscription concurrency for parallelism
        val concurrency = if (channelConcurrency == -1) Settings.instance.subscriptions.getSubscriptionsConcurrency() else channelConcurrency;
        val deferred = profile.ownedClaims.groupBy { it.claim.claimType }
            .mapNotNull {
                val url = it.value.firstOrNull()?.claim?.resolveChannelUrl() ?: return@mapNotNull null;
                val client = StatePlatform.instance.getChannelClientOrNull(url) ?: return@mapNotNull null;

                return@mapNotNull Pair(client, scope.async(Dispatchers.IO) {
                    try {
                        return@async StatePlatform.instance.getChannelContent(url, isSubscriptionOptimized, concurrency);
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
    fun getSystemComments(context: Context, system: PublicKey): List<IPlatformComment> {
        if (!enabled) {
            return listOf()
        }

        val dp_25 = 25.dp(context.resources)
        val systemState = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(system))
        val author = system.systemToURLInfoSystemLinkUrl(systemState.servers.asIterable())
        val posts = arrayListOf<PolycentricPlatformComment>()
        Store.instance.enumerateSignedEvents(system, ContentType.POST) { se ->
            val ev = se.event
            val post = Protocol.Post.parseFrom(ev.content)

            posts.add(PolycentricPlatformComment(
                contextUrl = author,
                author = PlatformAuthorLink(
                    id = PlatformID("polycentric", author, null, ClaimType.POLYCENTRIC.value.toInt()),
                    name = systemState.username,
                    url = author,
                    thumbnail = systemState.avatar?.selectBestImage(dp_25 * dp_25)?.let { img -> img.toURLInfoSystemLinkUrl(system.toProto(), img.process, listOf(PolycentricCache.SERVER)) },
                    subscribers = null
                ),
                msg = if (post.content.count() > PolycentricPlatformComment.MAX_COMMENT_SIZE) post.content.substring(0, PolycentricPlatformComment.MAX_COMMENT_SIZE) else post.content,
                rating = RatingLikeDislikes(0, 0),
                date = if (ev.unixMilliseconds != null) Instant.ofEpochMilli(ev.unixMilliseconds!!).atOffset(ZoneOffset.UTC) else OffsetDateTime.MIN,
                replyCount = 0,
                eventPointer = se.toPointer(),
                parentReference = se.event.references.getOrNull(0)
            ))
        }

        return posts
    }

    data class LikesDislikesReplies(
        var likes: Long,
        var dislikes: Long,
        var replyCount: Long
    )

    suspend fun getLikesDislikesReplies(reference: Protocol.Reference): LikesDislikesReplies {
        ensureEnabled()

        val response = ApiMethods.getQueryReferences(PolycentricCache.SERVER, reference, null,
            null,
            listOf(
                Protocol.QueryReferencesRequestCountLWWElementReferences.newBuilder()
                    .setFromType(ContentType.OPINION.value)
                    .setValue(ByteString.copyFrom(Opinion.like.data))
                    .build(),
                Protocol.QueryReferencesRequestCountLWWElementReferences.newBuilder()
                    .setFromType(ContentType.OPINION.value)
                    .setValue(ByteString.copyFrom(Opinion.dislike.data))
                    .build()
            ),
            listOf(
                Protocol.QueryReferencesRequestCountReferences.newBuilder()
                    .setFromType(ContentType.POST.value)
                    .build()
            )
        );

        val likes = response.countsList[0];
        val dislikes = response.countsList[1];
        val replyCount = response.countsList[2];
        return LikesDislikesReplies(likes, dislikes, replyCount)
    }

    suspend fun getComment(contextUrl: String, reference: Reference): PolycentricPlatformComment {
        ensureEnabled()

        if (reference.referenceType != 2L) {
            throw Exception("Not a pointer")
        }

        val pointer = Protocol.Pointer.parseFrom(reference.reference)
        val events = ApiMethods.getEvents(PolycentricCache.SERVER, pointer.system, Protocol.RangesForSystem.newBuilder()
            .addRangesForProcesses(Protocol.RangesForProcess.newBuilder()
                .setProcess(pointer.process)
                .addRanges(Protocol.Range.newBuilder()
                    .setLow(pointer.logicalClock)
                    .setHigh(pointer.logicalClock)
                    .build())
                .build())
            .build())

        val sev = SignedEvent.fromProto(events.getEvents(0))
        val ev = sev.event

        if (ev.contentType != ContentType.POST.value) {
            throw Exception("This is not a comment")
        }

        val post = Protocol.Post.parseFrom(ev.content);
        val systemLinkUrl = ev.system.systemToURLInfoSystemLinkUrl(listOf(PolycentricCache.SERVER));
        val dp_25 = 25.dp(StateApp.instance.context.resources)

        val profileEvents = ApiMethods.getQueryLatest(
            PolycentricCache.SERVER,
            ev.system.toProto(),
            listOf(
                ContentType.AVATAR.value,
                ContentType.USERNAME.value
            )
        ).eventsList.map { e -> SignedEvent.fromProto(e) }.groupBy { e -> e.event.contentType }
            .map { (_, events) -> events.maxBy { x -> x.event.unixMilliseconds ?: 0 } };

        val nameEvent = profileEvents.firstOrNull { e -> e.event.contentType == ContentType.USERNAME.value };
        val avatarEvent = profileEvents.firstOrNull { e -> e.event.contentType == ContentType.AVATAR.value };
        val imageBundle = if (avatarEvent != null) {
            val lwwElementValue = avatarEvent.event.lwwElement?.value;
            if (lwwElementValue != null) {
                Protocol.ImageBundle.parseFrom(lwwElementValue)
            } else {
                null
            }
        } else {
            null
        }

        val ldr = getLikesDislikesReplies(reference)
        return PolycentricPlatformComment(
            contextUrl = contextUrl,
            author = PlatformAuthorLink(
                id = PlatformID("polycentric", systemLinkUrl, null, ClaimType.POLYCENTRIC.value.toInt()),
                name = nameEvent?.event?.lwwElement?.value?.decodeToString() ?: "Unknown",
                url = systemLinkUrl,
                thumbnail =  imageBundle?.selectBestImage(dp_25 * dp_25)?.let { img -> img.toURLInfoSystemLinkUrl(ev.system.toProto(), img.process, listOf(PolycentricCache.SERVER)) },
                subscribers = null
            ),
            msg = if (post.content.count() > PolycentricPlatformComment.MAX_COMMENT_SIZE) post.content.substring(0, PolycentricPlatformComment.MAX_COMMENT_SIZE) else post.content,
            rating = RatingLikeDislikes(ldr.likes, ldr.dislikes),
            date = if (ev.unixMilliseconds != null) Instant.ofEpochMilli(ev.unixMilliseconds!!).atOffset(ZoneOffset.UTC) else OffsetDateTime.MIN,
            replyCount = ldr.replyCount.toInt(),
            eventPointer = sev.toPointer(),
            parentReference = sev.event.references.getOrNull(0)
        )
    }

    suspend fun getCommentPager(contextUrl: String, reference: Protocol.Reference, extraByteReferences: List<ByteArray>? = null): IPager<IPlatformComment> {
        if (!enabled) {
            return EmptyPager()
        }

        val response = ApiMethods.getQueryReferences(PolycentricCache.SERVER, reference, null,
            Protocol.QueryReferencesRequestEvents.newBuilder()
                .setFromType(ContentType.POST.value)
                .addAllCountLwwElementReferences(arrayListOf(
                    Protocol.QueryReferencesRequestCountLWWElementReferences.newBuilder()
                        .setFromType(ContentType.OPINION.value)
                        .setValue(ByteString.copyFrom(Opinion.like.data))
                        .build(),
                    Protocol.QueryReferencesRequestCountLWWElementReferences.newBuilder()
                        .setFromType(ContentType.OPINION.value)
                        .setValue(ByteString.copyFrom(Opinion.dislike.data))
                        .build()
                ))
                .addCountReferences(
                    Protocol.QueryReferencesRequestCountReferences.newBuilder()
                    .setFromType(ContentType.POST.value)
                    .build())
                .build(),
            extraByteReferences = extraByteReferences
        );

        val results = mapQueryReferences(contextUrl, response);
        val nextCursor = if (response.hasCursor()) response.cursor.toByteArray() else null
        return object : IAsyncPager<IPlatformComment>, IPager<IPlatformComment> {
            private var _results: List<IPlatformComment> = results
            private var _cursor: ByteArray? = nextCursor

            override fun hasMorePages(): Boolean {
                return _cursor != null;
            }

            override fun nextPage() {
                runBlocking { nextPageAsync() }
            }

            override suspend fun nextPageAsync() {
                val nextPageResponse = ApiMethods.getQueryReferences(PolycentricCache.SERVER, reference, _cursor,
                    Protocol.QueryReferencesRequestEvents.newBuilder()
                        .setFromType(ContentType.POST.value)
                        .addAllCountLwwElementReferences(arrayListOf(
                            Protocol.QueryReferencesRequestCountLWWElementReferences.newBuilder()
                                .setFromType(ContentType.OPINION.value)
                                .setValue(ByteString.copyFrom(Opinion.like.data))
                                .build(),
                            Protocol.QueryReferencesRequestCountLWWElementReferences.newBuilder()
                                .setFromType(ContentType.OPINION.value)
                                .setValue(ByteString.copyFrom(Opinion.dislike.data))
                                .build()
                        ))
                        .addCountReferences(
                            Protocol.QueryReferencesRequestCountReferences.newBuilder()
                                .setFromType(ContentType.POST.value)
                                .build())
                        .build()
                );

                _cursor = if (nextPageResponse.hasCursor()) nextPageResponse.cursor.toByteArray() else null
                _results = mapQueryReferences(contextUrl, nextPageResponse)
            }

            override fun getResults(): List<IPlatformComment> {
                return _results;
            }
        };
    }

    private suspend fun mapQueryReferences(contextUrl: String, response: Protocol.QueryReferencesResponse): List<IPlatformComment> {
        return response.itemsList.mapNotNull {
            val sev = SignedEvent.fromProto(it.event);
            val ev = sev.event;
            if (ev.contentType != ContentType.POST.value) {
                return@mapNotNull null;
            }

            try {
                val post = Protocol.Post.parseFrom(ev.content);
                val likes = it.countsList[0];
                val dislikes = it.countsList[1];
                val replies = it.countsList[2];

                val scope = StateApp.instance.scopeOrNull ?: return@mapNotNull null;
                return@mapNotNull LazyComment(scope.async(_commentPoolDispatcher){
                    Logger.i(TAG, "Fetching comment data for [" + ev.system.key.toBase64() + "]");
                    val profileEvents = ApiMethods.getQueryLatest(
                        PolycentricCache.SERVER,
                        ev.system.toProto(),
                        listOf(
                            ContentType.AVATAR.value,
                            ContentType.USERNAME.value
                        )
                    ).eventsList.map { e -> SignedEvent.fromProto(e) }.groupBy { e -> e.event.contentType }
                        .map { (_, events) -> events.maxBy { x -> x.event.unixMilliseconds ?: 0 } };

                    val nameEvent = profileEvents.firstOrNull { e -> e.event.contentType == ContentType.USERNAME.value };
                    val avatarEvent = profileEvents.firstOrNull { e -> e.event.contentType == ContentType.AVATAR.value };
                    val imageBundle = if (avatarEvent != null) {
                        val lwwElementValue = avatarEvent.event.lwwElement?.value;
                        if (lwwElementValue != null) {
                            Protocol.ImageBundle.parseFrom(lwwElementValue)
                        } else {
                            null
                        }
                    } else {
                        null
                    }

                    val unixMilliseconds = ev.unixMilliseconds
                    //TODO: Don't use single hardcoded sderver here
                    val systemLinkUrl = ev.system.systemToURLInfoSystemLinkUrl(listOf(PolycentricCache.SERVER));
                    val dp_25 = 25.dp(StateApp.instance.context.resources)
                    return@async PolycentricPlatformComment(
                        contextUrl = contextUrl,
                        author = PlatformAuthorLink(
                            id = PlatformID("polycentric", systemLinkUrl, null, ClaimType.POLYCENTRIC.value.toInt()),
                            name = nameEvent?.event?.lwwElement?.value?.decodeToString() ?: "Unknown",
                            url = systemLinkUrl,
                            thumbnail =  imageBundle?.selectBestImage(dp_25 * dp_25)?.let { img -> img.toURLInfoSystemLinkUrl(ev.system.toProto(), img.process, listOf(PolycentricCache.SERVER)) },
                            subscribers = null
                        ),
                        msg = if (post.content.count() > PolycentricPlatformComment.MAX_COMMENT_SIZE) post.content.substring(0, PolycentricPlatformComment.MAX_COMMENT_SIZE) else post.content,
                        rating = RatingLikeDislikes(likes, dislikes),
                        date = if (unixMilliseconds != null) Instant.ofEpochMilli(unixMilliseconds).atOffset(ZoneOffset.UTC) else OffsetDateTime.MIN,
                        replyCount = replies.toInt(),
                        eventPointer = sev.toPointer(),
                        parentReference = sev.event.references.getOrNull(0)
                    );
                });
            } catch (e: Throwable) {
                return@mapNotNull null;
            }
        };
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