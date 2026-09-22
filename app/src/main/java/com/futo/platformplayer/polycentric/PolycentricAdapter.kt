package com.futo.platformplayer.polycentric

import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.states.StateApp
import org.futo.polycentric.core.AndroidFileStoreDriver
import org.futo.polycentric.core.Collections
import org.futo.polycentric.core.PolycentricClient
import org.futo.polycentric.core.SqliteStorageDriver
import org.futo.polycentric.core.SyncStrategy
import org.futo.polycentric.ffi.PolycentricCore
import polycentric.v2.Application
import polycentric.v2.Content
import polycentric.v2.Event
import polycentric.v2.EventBundle
import polycentric.v2.EventKey
import polycentric.v2.ImageSet
import polycentric.v2.ProfileUpdate
import java.io.File

/**
 * Adapter that maps Harbor/Polycentric's kotlin-core SDK
 * (`org.futo.polycentric.core`) to Grayjay-specific interfaces.
 */
class PolycentricAdapter(internal val client: PolycentricClient) {

    /**
     * Build the adapter, which manages a polycentric client, with default arguments.
     */
    constructor() : this(buildDefaultClient())

    /** Initialize client with local store: call once after construction. */
    suspend fun initialize() = client.initialize()

    /** Whether [initialize] has finished and the keypair is set. */
    val isReady: Boolean get() = client.isReady

    /** Suspends until [initialize] completes; throws if it failed. */
    suspend fun awaitReady() = client.awaitReady()

    /**
     * The active identity key, or an [IllegalStateException] when nobody is
     * logged in. Called at the head of every write path.
     */
    internal fun requireActiveIdentity(): String =
        client.activeIdentityKey
            ?: throw IllegalStateException("Not logged in to Polycentric")

    /**
     * Build, sign, commit locally, and push one event.
     */
    internal suspend fun publish(content: Content, collection: Int): EventKey {
        val event = client.buildEvent(content, collection)
        val signed = client.signEvent(event)
        client.commitEvent(signed, content)
        client.sync(SyncStrategy.PARTIAL_PUSH)
        return event.key ?: error("built event has no key")
    }

    /** Largest variant URL from an image set (Harbor uploads smallest-first). */
    internal fun bestBlobUrl(set: ImageSet?): String? =
        set?.images?.maxByOrNull { it.width }?.blob?.digest?.let { client.blobUrl(it) }

    /** Get the latest `ProfileUpdate` by checking sequence numbers. */
    internal fun latestProfileUpdate(bundles: List<EventBundle>): ProfileUpdate? =
        bundles.mapNotNull { b ->
            val signed = b.signed_event ?: return@mapNotNull null
            val event = Event.ADAPTER.decode(signed.event_bytes)
            val bytes = b.serialized_content?.content_bytes ?: return@mapNotNull null
            val update = Content.ADAPTER.decode(bytes).profile_update ?: return@mapNotNull null
            (event.key?.sequence ?: 0L) to update
        }.maxByOrNull { it.first }?.second

    /**
     * Canonicalize a video URL so the same video shares one Polycentric
     * thread. See [normalizeVideoUrl] in `UrlCanonicalization.kt` for the
     * generic normalization rules.
     *
     * TODO: Platform plugins should provide platform-specific normalization
     * rules.
     */
    internal fun normalizeUrl(url: String): String = normalizeVideoUrl(url)

    /**
     * Returns a sequence of all non-deleted events authored by the active identity.
     */
	internal fun myInteractions(): Sequence<Pair<EventKey, Content>> {
        val identity = client.activeIdentityKey ?: return emptySequence()
        return client.listValidEvents(identity, Collections.INTERACTIONS)
        .asSequence()
        .mapNotNull { bundle ->
            val signed = bundle.signed_event ?: return@mapNotNull null
            val event = Event.ADAPTER.decode(signed.event_bytes)
            val reactionKey = event.key ?: return@mapNotNull null
            val bytes = bundle.serialized_content?.content_bytes ?: return@mapNotNull null
            reactionKey to Content.ADAPTER.decode(bytes)
        }
    }

    companion object {

        /**
         * TODO: make the seed server list configurable, rather than hard-coded.
         */
        val SEED_SERVERS: List<String> = listOf("https://srv.harbor.social")

        /**
         * Construct a Harbor client using the Kotlin SDK, using Grayjay's
         * global app context to construct arguments.
         */
        private fun buildDefaultClient(): PolycentricClient {
            val appContext = StateApp.instance.context.applicationContext
            return PolycentricClient(
                core = PolycentricCore(),
                storageDriver = PolycentricStorageDriver(appContext),
                filestore = AndroidFileStoreDriver(File(appContext.filesDir, "polycentric_blobs")),
                seedServers = SEED_SERVERS,
                // We declare the Harbor/Polycentric application field on every event the Grayjay
                // client builds.
                application = Application(
                    name = "Grayjay",
                    id = appContext.packageName,
                    version = BuildConfig.VERSION_NAME,
                    url = "https://grayjay.app",
                ),
            )
        }
    }
}
