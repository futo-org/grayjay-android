package com.futo.platformplayer.states

import android.content.Context
import android.content.Intent
import com.futo.platformplayer.R
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.activities.PolycentricCreateProfileActivity
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.polycentric.claimMigratedIdentity
import com.futo.platformplayer.polycentric.LegacyKeyAlreadyClaimedException
import com.futo.platformplayer.polycentric.markLegacyKeyMigrated
import com.futo.platformplayer.polycentric.recordLegacyKeys
import com.futo.platformplayer.polycentric.legacyKeyPairs
import com.futo.platformplayer.polycentric.lookupMigratedIdentity
import com.futo.platformplayer.polycentric.findKeyPair
import com.futo.platformplayer.polycentric.forgetLegacyKey
import com.futo.platformplayer.polycentric.importLegacyKey
import com.futo.platformplayer.polycentric.parseLegacyExport
import com.futo.platformplayer.polycentric.PolycentricKeyStorage
import com.futo.platformplayer.toHexString
import java.util.concurrent.atomic.AtomicBoolean
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.comments.PolycentricPlatformComment
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.structures.EmptyPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.polycentric.ModerationsManager
import com.futo.platformplayer.polycentric.AttributedContent
import com.futo.platformplayer.polycentric.PolycentricAdapter
import com.futo.platformplayer.polycentric.getCommentPager
import com.futo.platformplayer.polycentric.deleteComment
import com.futo.platformplayer.polycentric.getLiveVideoRating
import com.futo.platformplayer.polycentric.getMyComments
import com.futo.platformplayer.polycentric.getReplies
import com.futo.platformplayer.polycentric.getThread
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
import com.futo.platformplayer.polycentric.getServers
import com.futo.platformplayer.polycentric.canEditServers
import com.futo.platformplayer.polycentric.addServer
import com.futo.platformplayer.polycentric.removeServer
import com.futo.platformplayer.polycentric.PairingExpiredException
import com.futo.platformplayer.polycentric.PairingPreview
import com.futo.platformplayer.polycentric.PendingClaim
import com.futo.platformplayer.polycentric.approveClaimer
import com.futo.platformplayer.polycentric.canIssuePairing
import com.futo.platformplayer.polycentric.cancelClaimer
import com.futo.platformplayer.polycentric.claimPaired
import com.futo.platformplayer.polycentric.isPairingExpiredError
import com.futo.platformplayer.polycentric.joinPairing
import com.futo.platformplayer.polycentric.pollAuthorization
import com.futo.platformplayer.polycentric.pollClaimers
import com.futo.platformplayer.polycentric.prepareClaimerKey
import com.futo.platformplayer.polycentric.previewPairing
import com.futo.platformplayer.polycentric.startIssuerSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import org.futo.polycentric.core.PairingSessionManager
import org.futo.polycentric.core.StoredKeyPair
import polycentric.v2.PairingInfo
import polycentric.v2.PublicKey
import polycentric.v2.EventKey
import kotlin.coroutines.resume


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
    private val _migrationStarted = AtomicBoolean(false)

    /**
     * Emitted on the main thread when identities are added or the active identity changes in the
     * background.
     */
    val onIdentitiesChanged = Event0()

    /** Emitted on the main thread after the active identity's server list changes. */
    val onServersChanged = Event0()

    fun load(context: Context) {
        if (!enabled) {
            return
        }

        _backgroundScope.launch {
            awaitAdapter() ?: return@launch
            migrateLegacyIdentities(context)
        }
    }

    /**
     * Claims the v2 identities that Harbor's Grayjay migrator service created for the legacy v1
     * keys on this device, then restores the user's session. A key that fails to migrate asks the
     * user to retry or delete it.
     */
    private suspend fun migrateLegacyIdentities(context: Context) {
        val migratorUrl = PolycentricAdapter.MIGRATOR_URL ?: return
        if (!enabled || !_migrationStarted.compareAndSet(false, true)) {
            return
        }

        val claimed = mutableListOf<String>()
        var createNew = false
        try {
            for (keyPair in adapter.legacyKeyPairs()) {
                when (val outcome = migrateLegacyKeyPair(context, migratorUrl, keyPair)) {
                    is LegacyKeyOutcome.Claimed -> claimed.add(outcome.identity)
                    LegacyKeyOutcome.CreateNew -> createNew = true
                    LegacyKeyOutcome.AlreadyClaimed, LegacyKeyOutcome.Skipped -> {}
                }
            }

            if (claimed.isNotEmpty()) {
                mutateIdentity {
                    // If no active identity, activate the newly migrated one
                    if (it.activeIdentity() == null) {
                        it.switchIdentity(claimed.first())
                    }
                }
            }
            Logger.i(TAG, "Legacy identity migration finished: claimed $claimed, create new $createNew, active identity ${adapter.activeIdentity()}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.e(TAG, "Legacy identity migration failed.", e)
        }

        if (claimed.isNotEmpty()) {
            val text = context.getString(
                if (claimed.size > 1) R.string.polycentric_migration_complete_multiple else R.string.polycentric_migration_complete
            )
            UIDialogs.appToast(text, long = true)
        }
        withContext(Dispatchers.Main) {
            if (claimed.isNotEmpty()) {
                onIdentitiesChanged.emit()
            }
            // If the user decided to skip migrating a v1 identity, prompt to create a new v2 identity.
            if (createNew && activeIdentity() == null) {
                StateApp.instance.activity?.let {
                    it.startActivity(Intent(it, PolycentricCreateProfileActivity::class.java))
                }
            }
        }
    }

    private sealed interface LegacyKeyOutcome {
        class Claimed(val identity: String) : LegacyKeyOutcome
        object CreateNew : LegacyKeyOutcome
        /** Another device with the same key migrated first; this device should pair instead. */
        object AlreadyClaimed : LegacyKeyOutcome
        /** The user dismissed the failure prompt; the key is retried on a later start. */
        object Skipped : LegacyKeyOutcome
    }

    /**
     * Looks up and claims the migrated identity of one legacy key. On failure, asks the user to
     * retry or to delete the key.
     */
    private suspend fun migrateLegacyKeyPair(context: Context, migratorUrl: String, keyPair: StoredKeyPair): LegacyKeyOutcome {
        // While loop used when the user selects to retry a key migration
        while (true) {
            Logger.i(TAG, "Migrating legacy key ${keyPair.publicKey.toHexString()}")
            val error = try {
                val identity = lookupMigratedIdentity(migratorUrl, keyPair.publicKey)
                    ?: throw IllegalStateException(context.getString(R.string.polycentric_migration_not_found))
                mutateIdentity { it.claimMigratedIdentity(keyPair, identity) }
                Logger.i(TAG, "Claimed migrated identity $identity")
                return LegacyKeyOutcome.Claimed(identity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LegacyKeyAlreadyClaimedException) {
                markLegacyKeyMigrated(keyPair)
                UIDialogs.appToastError(context.getString(R.string.polycentric_migration_already_claimed), true)
                return LegacyKeyOutcome.AlreadyClaimed
            } catch (e: Throwable) {
                Logger.w(TAG, "Failed to migrate legacy identity.", e)
                e
            }

            when (promptMigrationFailure(error)) {
                MigrationFailureChoice.RETRY -> continue
                MigrationFailureChoice.CREATE_NEW -> {
                    markLegacyKeyMigrated(keyPair)
                    return LegacyKeyOutcome.CreateNew
                }
                null -> return LegacyKeyOutcome.Skipped
            }
        }
    }

    private enum class MigrationFailureChoice { RETRY, CREATE_NEW }

    /** Shows the migration error and waits for the user's choice; null when dismissed. */
    private suspend fun promptMigrationFailure(error: Throwable): MigrationFailureChoice? = withContext(Dispatchers.Main) {
        val activity = StateApp.instance.activity ?: return@withContext null
        suspendCancellableCoroutine { continuation ->
            val choose = { choice: MigrationFailureChoice? ->
                if (continuation.isActive) {
                    // Implicitly returns choice from [suspendCancellableCoroutine]
                    continuation.resume(choice)
                }
            }
            val dialog = UIDialogs.showDialog(activity, R.drawable.ic_error_pred,
                activity.getString(R.string.polycentric_migration_failed),
                error.message ?: error.javaClass.simpleName, null, -1,
                UIDialogs.Action(activity.getString(R.string.polycentric_migration_create_new), {
                    choose(MigrationFailureChoice.CREATE_NEW)
                }, UIDialogs.ActionStyle.DANGEROUS),
                UIDialogs.Action(activity.getString(R.string.retry), {
                    choose(MigrationFailureChoice.RETRY)
                }, UIDialogs.ActionStyle.PRIMARY)
            )
            dialog.setOnCancelListener { choose(null) }
            continuation.invokeOnCancellation { dialog.dismiss() }
        }
    }

    sealed interface LegacyImportResult {
        class Migrated(val identity: String) : LegacyImportResult
        /** The key was already migrated on this device. */
        class AlreadyImported(val identity: String) : LegacyImportResult
        /** The migrator has no identity for the key. */
        object NotFound : LegacyImportResult
        /** Another device with the same key migrated first; this device should pair instead. */
        object AlreadyClaimed : LegacyImportResult
    }

    /**
     * Imports a Polycentric v1 profile export, claims the identity Harbor's Grayjay migrator
     * created for its key, and signs in to that identity. Throws on invalid exports and on lookup
     * or claim failures, in which case the key is kept such that the import can be retried.
     */
    suspend fun importLegacyExport(text: String): LegacyImportResult {
        val seed = parseLegacyExport(text)
        val publicKey = PolycentricKeyStorage.deriveEd25519PublicKey(seed)
        val adapter = requireAdapter()

        adapter.findKeyPair(publicKey)?.second?.let { identity ->
            switchIdentity(identity)
            withContext(Dispatchers.Main) { onIdentitiesChanged.emit() }
            return LegacyImportResult.AlreadyImported(identity)
        }

        val added = withContext(Dispatchers.IO) { importLegacyKey(seed) }
        val keyPair = adapter.findKeyPair(publicKey)?.first
            ?: throw IllegalStateException("Imported legacy key is not stored")
        val identity = lookupMigratedIdentity(PolycentricAdapter.MIGRATOR_URL, publicKey)
        if (identity == null) {
            if (added) {
                withContext(Dispatchers.IO) { forgetLegacyKey(publicKey) }
            }
            return LegacyImportResult.NotFound
        }

        try {
            mutateIdentity {
                it.claimMigratedIdentity(keyPair, identity)
                it.switchIdentity(identity)
            }
        } catch (e: LegacyKeyAlreadyClaimedException) {
            withContext(Dispatchers.IO) {
                if (added) forgetLegacyKey(publicKey) else markLegacyKeyMigrated(keyPair)
            }
            return LegacyImportResult.AlreadyClaimed
        }
        Logger.i(TAG, "Imported and claimed migrated identity $identity")
        withContext(Dispatchers.Main) { onIdentitiesChanged.emit() }
        return LegacyImportResult.Migrated(identity)
    }

    fun ensureEnabled() {
        if (!enabled) {
            throw IllegalStateException("Harbor is disabled")
        }
    }

    val isLoggedIn: Boolean
        get() = enabled && adapter.isReady && adapter.isLoggedIn

    /** Runs a serialized, non-cancellable identity mutation. */
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

    suspend fun getServers(): List<String> {
        ensureEnabled()
        val adapter = awaitAdapter() ?: throw IllegalStateException("Harbor is disabled")
        return adapter.getServers()
    }

    suspend fun canEditServers(): Boolean {
        ensureEnabled()
        val adapter = awaitAdapter() ?: throw IllegalStateException("Harbor is disabled")
        return adapter.canEditServers()
    }

    /**
     * Adds a server to the active identity's server list. Throws
     * [org.futo.polycentric.core.ServerAlreadyAddedException] when it is already listed.
     */
    suspend fun addServer(url: String) {
        mutateIdentity { it.addServer(url) }
        withContext(Dispatchers.Main) { onServersChanged.emit() }
    }

    /**
     * Removes a server from the active identity's server list. The last server is prevented from
     * being removed.
     */
    suspend fun removeServer(url: String) {
        mutateIdentity {
            if (it.getServers().size <= 1) {
                throw IllegalStateException(StateApp.instance.context.getString(R.string.harbor_server_last_required))
            }
            it.removeServer(url)
        }
        withContext(Dispatchers.Main) { onServersChanged.emit() }
    }

    /** State for a pending claim (as a claimer). See `PolycentricPairing.kt`. */
    private var _pendingClaim: PendingClaim? = null

    /** Whether this device can approve another device into the active identity. */
    suspend fun canIssuePairing(): Boolean {
        ensureEnabled()
        return requireAdapter().canIssuePairing()
    }

    suspend fun startIssuerPairing(): PairingSessionManager.PairingSession = mapPairingErrors {
        requireAdapter().startIssuerSession()
    }

    suspend fun pollPairingClaimers(info: PairingInfo): List<PublicKey> = mapPairingErrors {
        requireAdapter().pollClaimers(info)
    }

    suspend fun approvePairingClaimer(session: PairingSessionManager.PairingSession, key: PublicKey, asRotationKey: Boolean) =
        mapPairingErrors { mutateIdentity { it.approveClaimer(session, key, asRotationKey) } }

    suspend fun previewPairing(info: PairingInfo): PairingPreview = mapPairingErrors {
        requireAdapter().previewPairing(info)
    }

    /**
     * Create a new pending claim with a new key pair. Cancels any earlier unfinished pairing first.
     */
    suspend fun preparePairingClaimer(): PublicKey = mutateIdentity { adapter ->
        _pendingClaim?.let { adapter.cancelClaimer(it) }
        _pendingClaim = null
        val pending = adapter.prepareClaimerKey()
        _pendingClaim = pending
        pending.publicKey
    }

    suspend fun joinPairing(info: PairingInfo) = mapPairingErrors {
        requireAdapter().joinPairing(info)
    }

    suspend fun pollPairingAuthorization(info: PairingInfo): Boolean = mapPairingErrors {
        requireAdapter().pollAuthorization(info)
    }

    /**
     * Claims the issuer's identity once the issuer has approved this device. On failure, restores
     * the identity that was active before pairing.
     */
    suspend fun claimPairedIdentity(session: PairingSessionManager.PairingSession) {
        mapPairingErrors {
            mutateIdentity { adapter ->
                val pending = _pendingClaim ?: throw IllegalStateException("No pairing in progress")
                try {
                    adapter.claimPaired(pending, session)
                } catch (e: Throwable) {
                    _pendingClaim = null
                    try {
                        adapter.cancelClaimer(pending)
                    } catch (cancelError: Throwable) {
                        Logger.e(TAG, "Failed to restore the identity after a failed claim.", cancelError)
                    }
                    throw e
                }
                _pendingClaim = null
            }
        }
        withContext(Dispatchers.Main) {
            onIdentitiesChanged.emit()
        }
    }

    /** Abandons an unfinished pairing and restores the identity that was active before it. */
    suspend fun cancelPairingClaimer() = mutateIdentity { adapter ->
        val pending = _pendingClaim ?: return@mutateIdentity
        _pendingClaim = null
        adapter.cancelClaimer(pending)
    }

    /**
     * Converts polycentric-core's expired session error into a [PairingExpiredException].
     * TODO: the polycentric-core's expired session error should be refactored into its own
     * exception type, so that string matching isn't needed.
     */
    private inline fun <T> mapPairingErrors(block: () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (isPairingExpiredError(e)) throw PairingExpiredException(e)
            throw e
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
                // Before the SDK may create keys of its own
                withContext(Dispatchers.IO) { recordLegacyKeys(StateApp.instance.context) }
                adapter.initialize()
            }
        }
        return adapter
    }

    /** Labels the user chose to hide. */
    private fun omitLabels() = ModerationsManager.getInstance().omitLabels()

    suspend fun getCommentPager(videoUrl: String): IPager<IPlatformComment> {
        val adapter = awaitAdapter() ?: return EmptyPager()
        return try {
            adapter.getCommentPager(videoUrl, omitLabels())
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to load comments.", e)
            EmptyPager()
        }
    }

    suspend fun getReplies(comment: PolycentricPlatformComment): IPager<IPlatformComment> {
        val adapter = awaitAdapter() ?: return EmptyPager()
        return try {
            adapter.getReplies(comment, omitLabels())
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to load replies.", e)
            EmptyPager()
        }
    }

    /** The comment at [key] and a page of replies, or null if it could not be loaded. */
    suspend fun getThread(key: EventKey, contextUrl: String): Pair<PolycentricPlatformComment, IPager<IPlatformComment>>? {
        val adapter = awaitAdapter() ?: return null
        return try {
            adapter.getThread(key, contextUrl, omitLabels())
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to load comment thread.", e)
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
    suspend fun postComment(videoUrl: String, video: AttributedContent?, text: String): PolycentricPlatformComment {
        awaitAdapter() ?: throw IllegalStateException("Harbor is disabled")
        return adapter.postComment(videoUrl, video, text)
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
        @Synchronized
        fun dispose() {
            _instance?.cleanup()
            _instance = null;
        }
    }
}
