package com.futo.platformplayer.polycentric

import com.futo.polycentric.core.*
import userpackage.Protocol
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.constructs.BatchedTaskHandler
import com.futo.platformplayer.fragment.mainactivity.main.PolycentricProfile
import com.futo.platformplayer.getNowDiffSeconds
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.resolveChannelUrl
import com.futo.platformplayer.resolveChannelUrls
import com.futo.platformplayer.serializers.OffsetDateTimeSerializer
import com.futo.platformplayer.stores.CachedPolycentricProfileStorage
import com.futo.platformplayer.stores.FragmentedStorage
import com.google.protobuf.ByteString
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.time.OffsetDateTime

class PolycentricCache {
    data class CachedOwnedClaims(val ownedClaims: List<OwnedClaim>?, val creationTime: OffsetDateTime = OffsetDateTime.now());
    @Serializable
    data class CachedPolycentricProfile(val profile: PolycentricProfile?, @Serializable(with = OffsetDateTimeSerializer::class) val creationTime: OffsetDateTime = OffsetDateTime.now());

    private val _cacheExpirationSeconds = 60 * 60 * 3;
    private val _cache = hashMapOf<PlatformID, CachedOwnedClaims>()
    private val _profileCache = hashMapOf<PublicKey, CachedPolycentricProfile>()
    private val _profileUrlCache = FragmentedStorage.get<CachedPolycentricProfileStorage>("profileUrlCache")
    private val _scope = CoroutineScope(Dispatchers.IO);

    private val _taskGetProfile = BatchedTaskHandler<PublicKey, CachedPolycentricProfile>(_scope, { system ->
            val signedProfileEvents = ApiMethods.getQueryLatest(
                SERVER,
                system.toProto(),
                listOf(
                    ContentType.BANNER.value,
                    ContentType.AVATAR.value,
                    ContentType.USERNAME.value,
                    ContentType.DESCRIPTION.value,
                    ContentType.STORE.value,
                    ContentType.SERVER.value,
                    ContentType.STORE_DATA.value,
                    ContentType.PROMOTION_BANNER.value,
                    ContentType.PROMOTION.value,
                    ContentType.MEMBERSHIP_URLS.value,
                    ContentType.DONATION_DESTINATIONS.value
                )
            ).eventsList.map { e -> SignedEvent.fromProto(e) };

            val storageSystemState = StorageTypeSystemState.create()
            for (signedEvent in signedProfileEvents) {
                storageSystemState.update(signedEvent.event)
            }

            val signedClaimEvents = ApiMethods.getQueryIndex(
                SERVER,
                system.toProto(),
                ContentType.CLAIM.value,
                limit = 200
            ).eventsList.map { e -> SignedEvent.fromProto(e) };

            val ownedClaims: ArrayList<OwnedClaim> = arrayListOf()
            for (signedEvent in signedClaimEvents) {
                if (signedEvent.event.contentType != ContentType.CLAIM.value) {
                    continue;
                }

                val response = ApiMethods.getQueryReferences(
                    SERVER,
                    Protocol.Reference.newBuilder()
                        .setReference(signedEvent.toPointer().toProto().toByteString())
                        .setReferenceType(2)
                        .build(),
                    null,
                    Protocol.QueryReferencesRequestEvents.newBuilder()
                        .setFromType(ContentType.VOUCH.value)
                        .build()
                );

                val ownedClaim = response.itemsList.map { SignedEvent.fromProto(it.event) }.getClaimIfValid(signedEvent);
                if (ownedClaim != null) {
                    ownedClaims.add(ownedClaim);
                }
            }

            Logger.i(TAG, "Retrieved profile (ownedClaims = $ownedClaims)");
            val systemState = SystemState.fromStorageTypeSystemState(storageSystemState);
            return@BatchedTaskHandler CachedPolycentricProfile(PolycentricProfile(system, systemState, ownedClaims));
        },
        { system -> return@BatchedTaskHandler getCachedProfile(system); },
        { system, result ->
            synchronized(_cache) {
                _profileCache[system] = result;

                if (result.profile != null) {
                    for (claim in result.profile.ownedClaims) {
                        val urls = claim.claim.resolveChannelUrls();
                        for (url in urls)
                            _profileUrlCache.map[url] = result;
                    }
                }

                _profileUrlCache.save();
            }
        });

    private val _batchTaskGetClaims = BatchedTaskHandler<PlatformID, CachedOwnedClaims>(_scope,
        { id ->
            val resolved = if (id.claimFieldType == -1) ApiMethods.getResolveClaim(SERVER, system, id.claimType.toLong(), id.value!!)
                else ApiMethods.getResolveClaim(SERVER, system, id.claimType.toLong(), id.claimFieldType.toLong(), id.value!!);
            Logger.v(TAG, "getResolveClaim(url = $SERVER, system = $system, id = $id, claimType = ${id.claimType}, matchAnyField = ${id.value})");
            val protoEvents = resolved.matchesList.flatMap { arrayListOf(it.claim).apply { addAll(it.proofChainList) } }
            val resolvedEvents = protoEvents.map { i -> SignedEvent.fromProto(i) };
            return@BatchedTaskHandler CachedOwnedClaims(resolvedEvents.getValidClaims());
        },
        { id -> return@BatchedTaskHandler getCachedValidClaims(id); },
        { id, result ->
            synchronized(_cache) {
                _cache[id] = result;
            }
        });

    private val _batchTaskGetData = BatchedTaskHandler<String, ByteBuffer>(_scope,
        {
            val urlData = if (it.startsWith("polycentric://")) {
                it.substring("polycentric://".length)
            } else it;

            val urlBytes = urlData.base64UrlToByteArray();
            val urlInfo = Protocol.URLInfo.parseFrom(urlBytes);
            if (urlInfo.urlType != 4L) {
                throw Exception("Only URLInfoDataLink is supported");
            }

            val dataLink = Protocol.URLInfoDataLink.parseFrom(urlInfo.body);
            return@BatchedTaskHandler ApiMethods.getDataFromServerAndReassemble(dataLink);
        },
        { return@BatchedTaskHandler null },
        { _, _ -> });

    fun getCachedValidClaims(id: PlatformID, ignoreExpired: Boolean = false): CachedOwnedClaims? {
        if (id.claimType <= 0) {
            return CachedOwnedClaims(null);
        }

        synchronized(_cache) {
            val cached = _cache[id]
            if (cached == null) {
                return null
            }

            if (!ignoreExpired && cached.creationTime.getNowDiffSeconds() > _cacheExpirationSeconds) {
                return  null;
            }

            return cached;
        }
    }

    //TODO: Review all return null in this file, perhaps it should be CachedX(null) instead
    fun getValidClaimsAsync(id: PlatformID): Deferred<CachedOwnedClaims> {
        if (id.value == null || id.claimType <= 0) {
            return _scope.async { CachedOwnedClaims(null) };
        }

        Logger.v(TAG, "getValidClaims (id: $id)")
        val def = _batchTaskGetClaims.execute(id);
        def.invokeOnCompletion {
            if (it == null) {
                return@invokeOnCompletion
            }

            handleException(it, handleNetworkException = { /* Do nothing (do not cache) */ }, handleOtherException = {
                //Cache failed result
                synchronized(_cache) {
                    _cache[id] = CachedOwnedClaims(null);
                }
            })
        };
        return def;
    }

    fun getDataAsync(url: String): Deferred<ByteBuffer> {
        return _batchTaskGetData.execute(url);
    }

    fun getCachedProfile(url: String, ignoreExpired: Boolean = false): CachedPolycentricProfile? {
        synchronized (_profileCache) {
            val cached = _profileUrlCache.get(url) ?: return null;
            if (!ignoreExpired && cached.creationTime.getNowDiffSeconds() > _cacheExpirationSeconds) {
                return  null;
            }

            return cached;
        }
    }

    fun getCachedProfile(system: PublicKey, ignoreExpired: Boolean = false): CachedPolycentricProfile? {
        synchronized(_profileCache) {
            val cached = _profileCache[system] ?: return null;
            if (!ignoreExpired && cached.creationTime.getNowDiffSeconds() > _cacheExpirationSeconds) {
                return null;
            }

            return cached;
        }
    }

    suspend fun getProfileAsync(id: PlatformID): CachedPolycentricProfile? {
        if (id.claimType <= 0) {
            return CachedPolycentricProfile(null);
        }

        val cachedClaims = getCachedValidClaims(id);
        if (cachedClaims != null) {
            if (!cachedClaims.ownedClaims.isNullOrEmpty()) {
                Logger.v(TAG, "getProfileAsync (id: $id) != null (with cached valid claims)")
                return getProfileAsync(cachedClaims.ownedClaims.first().system).await();
            } else {
                return null;
            }
        } else {
            Logger.v(TAG, "getProfileAsync (id: $id) no cached valid claims, will be retrieved")

            val claims = getValidClaimsAsync(id).await()
            if (!claims.ownedClaims.isNullOrEmpty()) {
                Logger.v(TAG, "getProfileAsync (id: $id) != null (with retrieved valid claims)")
                return getProfileAsync(claims.ownedClaims.first().system).await()
            } else {
                return null;
            }
        }
    }

    fun getProfileAsync(system: PublicKey): Deferred<CachedPolycentricProfile?> {
        Logger.i(TAG, "getProfileAsync (system: ${system})")
        val def = _taskGetProfile.execute(system);
        def.invokeOnCompletion {
            if (it == null) {
                return@invokeOnCompletion
            }

            handleException(it, handleNetworkException = { /* Do nothing (do not cache) */ }, handleOtherException = {
                //Cache failed result
                synchronized(_cache) {
                    val cachedProfile = CachedPolycentricProfile(null);
                    _profileCache[system] = cachedProfile;
                }
            })
        };
        return def;
    }

    private fun handleException(e: Throwable, handleNetworkException: () -> Unit, handleOtherException: () -> Unit) {
        val isNetworkException = when(e) {
            is java.net.UnknownHostException,
            is java.net.SocketTimeoutException,
            is java.net.ConnectException -> true
            else -> when(e.cause) {
                is java.net.UnknownHostException,
                is java.net.SocketTimeoutException,
                is java.net.ConnectException -> true
                else -> false
            }
        }
        if (isNetworkException) {
            handleNetworkException()
        } else {
            handleOtherException()
        }
    }

    companion object {
        private val system = Protocol.PublicKey.newBuilder()
            .setKeyType(1)
            .setKey(ByteString.copyFrom("gX0eCWctTm6WHVGot4sMAh7NDAIwWsIM5tRsOz9dX04=".base64ToByteArray())) //Production key
            //.setKey(ByteString.copyFrom("LeQkzn1j625YZcZHayfCmTX+6ptrzsA+CdAyq+BcEdQ".base64ToByteArray())) //Test key koen-futo
            .build();

        private const val TAG = "PolycentricCache"
        const val SERVER = "https://srv1-stg.polycentric.io"
        private var _instance: PolycentricCache? = null;

        @JvmStatic
        val instance: PolycentricCache
            get(){
            if(_instance == null)
                _instance = PolycentricCache();
            return _instance!!;
        };

        fun finish() {
            _instance?.let {
                _instance = null;
                it._scope.cancel("PolycentricCache finished");
            }
        }
    }
}