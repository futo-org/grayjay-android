package com.futo.platformplayer.polycentric

import com.futo.platformplayer.logging.Logger
import kotlinx.coroutines.CancellationException
import polycentric.v2.Event

/*
 * Workaround for a Polycentric core bug that leaves identity chains incomplete in local storage.
 *
 * `IdentityManager.claim` fetches the identity chain into the core's memory only, and the `sync`
 * that follows pulls a single page (at most 200 events, highest sequences first, across all
 * collections). For an identity with a long history, such as a migrated v1 identity with its
 * imported likes, that page holds no identity events at all, so the genesis is never saved. After a
 * restart, the core is hydrated from storage alone, the chain resolves to only the events this
 * device wrote, and every write fails with "No validated identity event at sequence N".
 *
 * TODO: remove once kt-core persists the identity chain on claim.
 */

private const val TAG = "PolycentricIdentityChain"

/**
 * Saves [identity]'s identity chain, as resolved from the core's memory, to local storage so that
 * it survives a restart. Call right after `IdentityManager.claim`, which fetched the chain. A
 * failure is only logged, since the claim itself succeeded.
 */
internal suspend fun PolycentricAdapter.saveClaimedIdentityChain(identity: String) {
    try {
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
        Logger.i(TAG, "Saved identity chain of $identity: ${chain.size} events, $saved newly saved")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Logger.w(TAG, "Failed to save the identity chain of claimed identity $identity", e)
    }
}
