package com.futo.platformplayer.polycentric

import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringArrayStorage
import kotlinx.coroutines.CancellationException
import org.futo.polycentric.core.Collections
import polycentric.v2.Event

/*
 * Workaround for a Polycentric core bug that leaves identity chains incomplete in local storage.
 *
 * `IdentityManager.claim` fetches the identity chain into the core's memory only, and the `sync`
 * that follows pulls a single page (at most 200 events, highest sequences first, across all
 * collections). For an identity with a long history, such as a migrated v1 identity with its
 * imported likes, that page holds no identity events at all. Later pulls send heads, so identity
 * events below this device's own head are never fetched. After a restart, the core is hydrated from
 * storage alone, the chain resolves to only its first few events, and every write fails with
 * "No validated identity event at sequence N".
 *
 * TODO: remove once kt-core persists the identity chain on claim and pages its pulls.
 */

private const val TAG = "PolycentricIdentityChain"

/** Server responses are capped at this many events. */
private const val IDENTITY_PAGE_SIZE = 200

/** Identities whose chain has been saved to local storage by [persistIdentityChain]. */
private val persistedChains get() = FragmentedStorage.get<StringArrayStorage>("polycentricPersistedChains")

/**
 * Fetches [identity]'s identity events from the seed servers and the client's servers, then saves
 * the resolved chain to local storage so that it survives a restart. Throws when the chain can't be
 * fetched or resolved.
 */
suspend fun PolycentricAdapter.persistIdentityChain(identity: String) {
    // Manually track "pagination" (sequence numbers) to pull all identity events from the server
    val servers = client.servers
    try {
        client.adoptServers((servers + PolycentricAdapter.SEED_SERVERS).distinct())
        var before: Long? = null
        do {
            val page = client.listEvents(identity, Collections.IDENTITY, limit = IDENTITY_PAGE_SIZE, sequenceLt = before)
            val oldest = page.mapNotNull { bundle ->
                bundle.signed_event?.let { Event.ADAPTER.decode(it.event_bytes).key?.sequence }
            }.minOrNull() ?: break
            before = oldest
        } while (page.size >= IDENTITY_PAGE_SIZE && oldest > 1)
    } finally {
        client.adoptServers(servers)
    }

    // Manually save each identity event we just pulled
    val chain = client.resolveIdentityChain(identity)
    var saved = 0
    for (bundle in chain) {
        val signed = bundle.signed_event ?: continue
        val event = Event.ADAPTER.decode(signed.event_bytes)
        val key = event.key ?: continue
        val digest = event.content_digest
        val contentBytes = bundle.serialized_content?.content_bytes
        if (digest != null && contentBytes != null && client.contents.get(digest) == null) {
            client.contents.save(digest, contentBytes.toByteArray())
        }
        if (client.events.getByEventKey(key) == null) {
            client.events.save(signed)
            saved++
        }
    }

    // Ensure that we set the servers to be the identity's latest server setting
    if (identity == client.activeIdentityKey) {
        client.refreshServers()
    }

    persistedChains.addDistinct(identity)
    persistedChains.saveBlocking()
    Logger.i(TAG, "Persisted identity chain of $identity: ${chain.size} events, $saved newly saved, servers ${client.servers}")
}

/**
 * Runs [persistIdentityChain] for each identity on this device that hasn't had its chain saved, to
 * repair devices that claimed an identity before this workaround. Failures are logged and retried
 * on the next start.
 */
suspend fun PolycentricAdapter.repairIdentityChains() {
    val persisted = persistedChains.getAllValues().toSet()
    for (identity in listIdentities().filter { it !in persisted }) {
        try {
            persistIdentityChain(identity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to repair the identity chain of $identity", e)
        }
    }
}

/**
 * [persistIdentityChain] after a successful claim. A failure is only logged, since the claim itself
 * succeeded: [repairIdentityChains] retries on the next start.
 */
internal suspend fun PolycentricAdapter.persistClaimedIdentityChain(identity: String) {
    try {
        persistIdentityChain(identity)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Logger.w(TAG, "Failed to persist the identity chain of claimed identity $identity", e)
    }
}
