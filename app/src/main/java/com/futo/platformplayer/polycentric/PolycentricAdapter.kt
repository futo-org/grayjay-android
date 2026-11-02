package com.futo.platformplayer.polycentric

import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.UIDialogs
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

    /**
     * The active identity key, or an [IllegalStateException] when nobody is
     * logged in. Called at the head of every write path.
     */
    internal fun requireActiveIdentity(): String =
        client.activeIdentityKey
            ?: throw IllegalStateException("Not logged in to Harbor")

    /**
     * Build, sign, commit locally, and push one event. The event stays in local storage whether
     * the push succeeds or not.
     *
     * TODO: when polycentric-core throws publish errors for us to catch, we should catch them and
     * display the error.
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
         * The initial servers of new identities.
         */
        val SEED_SERVERS: List<String> = listOf(
            "https://srv.harbor.social",
            "https://srv.polycentric.io",
        )

        /**
         * Base URL of the Harbor web app, used to build the outbound links
         * the comment and profile UI opens in a browser.
         *
         * TODO: make this configurable alongside the seed server list.
         */
        const val WEB_BASE_URL: String = "https://harbor.social"

        /**
         * Base URL of Harbor's `grayjay-migrator` lookup service, which maps legacy v1 system
         * keys to their migrated v2 identities. The v1 identity migration is skipped while unset.
         * Migrated identities live on [SEED_SERVERS] (the migrator's `IDENTITY_SERVERS`).
         */
        val MIGRATOR_URL: String = "https://grayjay-migrator.harbor.social"

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

/**
 * A short, user-facing description of why a Harbor write failed, suitable for appending to a
 * "Failed to ..." toast.
 */
fun Throwable.toHarborErrorMessage(): String = when (this) {
    is IllegalStateException, is IllegalArgumentException -> message ?: "Harbor is unavailable"
    else -> "Could not reach Harbor" + (message?.let { " ($it)" } ?: "")
}

/**
 * Shows "[prefix] [toHarborErrorMessage]" as a toast, with a button that copies the full error
 * (including causes and stack trace) for bug reports.
 */
fun showHarborErrorToast(prefix: String, e: Throwable) {
    val details = "Grayjay ${BuildConfig.VERSION_NAME}\n$prefix\n\n${e.stackTraceToString()}"
    UIDialogs.appToastWithDetails(prefix + " " + e.toHarborErrorMessage(), details)
}
