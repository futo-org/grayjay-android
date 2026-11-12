package com.futo.platformplayer.polycentric

import com.futo.platformplayer.logging.Logger
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.ByteString.Companion.toByteString
import org.futo.polycentric.core.IdentityManager
import org.futo.polycentric.core.PairingSessionManager
import org.futo.polycentric.core.StoredKeyPair
import org.futo.polycentric.core.SyncStrategy
import polycentric.v2.Content
import polycentric.v2.KeyType
import polycentric.v2.PairingInfo
import polycentric.v2.PublicKey
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64

/**
 * [PolycentricAdapter] functions for pairing two devices, an issuer and a claimer. The issuer is
 * a signed-in device that shares a pairing link. The claimer is a new device that opens the link,
 * waits for the issuer to authorize its key, then claims the identity as a second device attached
 * to the identity.
 */

/** Number of emojis in a pairing fingerprint. */
const val FINGERPRINT_LENGTH = 3

/** Formats [key] as `"<keyType>_<key in lowercase hex>"`. */
fun publicKeyToString(key: PublicKey): String = "${key.key_type.value}_${key.key.hex()}"

/**
 * The emojis both devices show as a visualization of the new device's [key] while pairing. The user
 * checks that they match.
 */
fun emojiFingerprint(key: PublicKey): List<String> = emojiFingerprintOfString(publicKeyToString(key))

/**
 * Picks [FINGERPRINT_LENGTH] emojis from [HARBOR_FINGERPRINT_POOL] based on the SHA-256 hash of
 * [text]. Each emoji uses the next 4 bytes of the hash as an unsigned number, wrapped to the pool
 * size. Mirrors Harbor's `publicKeyEmojiFingerprint`.
 */
fun emojiFingerprintOfString(text: String): List<String> {
    val hash = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
    val pool = HARBOR_FINGERPRINT_POOL
    return List(FINGERPRINT_LENGTH) { i ->
        val index = (hash.getInt(i * 4).toLong() and 0xFFFFFFFFL) % pool.size
        pool[index.toInt()]
    }
}

/** Path of Harbor's pairing link. */
const val PAIRING_LINK_PATH = "/login/pair"

/** Query parameter that holds the pairing code. */
const val PAIRING_CODE_PARAM = "code"

/**
 * Builds a link for another device to open:
 * `<baseUrl>/<PAIRING_LINK_PATH>?<PAIRING_CODE_PARAM>=<code>`. The code is [info] using URL-safe
 * base64 without padding.
 */
fun encodePairingLink(info: PairingInfo, baseUrl: String = PolycentricAdapter.WEB_BASE_URL): String {
    val code = Base64.getUrlEncoder().withoutPadding().encodeToString(PairingInfo.ADAPTER.encode(info))
    return baseUrl.toHttpUrl().newBuilder()
        .addPathSegments(PAIRING_LINK_PATH.removePrefix("/"))
        .addQueryParameter(PAIRING_CODE_PARAM, code)
        .build()
        .toString()
}

/**
 * Decodes [PairingInfo] from a pairing link or a code. Returns null if [input] is neither. A code
 * can be hex or base64. Mirrors Harbor's `extractPairingInfo`.
 */
fun parsePairingInfo(input: String): PairingInfo? {
    val trimmed = input.trim()
    var code = trimmed

    val link = pairingLinkUrl(trimmed)
    if (link != null) {
        // A link without a code is invalid.
        code = link.queryParameter(PAIRING_CODE_PARAM) ?: return null
    }

    return decodePairingCode(decodeHex(code)) ?: decodePairingCode(decodeBase64(code))
}

/** Whether [url] looks like a pairing link. */
fun isPairingLink(url: String): Boolean = pairingLinkUrl(url.trim()) != null

private fun pairingLinkUrl(text: String): HttpUrl? =
    text.toHttpUrlOrNull()?.takeIf { it.encodedPath.trimEnd('/') == PAIRING_LINK_PATH }

private fun decodePairingCode(bytes: ByteArray?): PairingInfo? {
    if (bytes == null || bytes.isEmpty()) return null
    val info = try {
        PairingInfo.ADAPTER.decode(bytes)
    } catch (_: Exception) {
        return null
    }
    if (info.server.isEmpty() || info.digest_sha256.size == 0) return null
    return info
}

private val HEX_REGEX = Regex("^[0-9a-fA-F]*$")
private val BASE64_REGEX = Regex("^[A-Za-z0-9+/_-]*={0,2}$")

private fun decodeHex(hex: String): ByteArray? {
    if (hex.length % 2 != 0 || !HEX_REGEX.matches(hex)) return null
    return ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

private fun decodeBase64(text: String): ByteArray? {
    if (!BASE64_REGEX.matches(text)) return null
    // Convert URL-safe base64 to standard base64
    val standard = text.replace('-', '+').replace('_', '/')
    return try {
        Base64.getDecoder().decode(standard)
    } catch (_: IllegalArgumentException) {
        null
    }
}

/** Raised when a pairing session has passed its expiration time. */
class PairingExpiredException(cause: Throwable? = null) : Exception("The pairing link expired", cause)

/** Raised when a claimer tries to pair into an identity that this device already holds. */
class PairingIdentityExistsException : IllegalStateException("This identity is already on this device")

/** Object containing the issuer's identity and, when available, its profile. */
class PairingPreview(
    val session: PairingSessionManager.PairingSession,
    val profile: PolycentricChannelProfile?,
) {
    val issuerIdentity: String get() = session.digest.issuer_identity
}

/**
 * A key pair created for a claim, plus the key pair and identity that were active before it, so
 * that a cancelled claim can restore them.
 */
class PendingClaim internal constructor(
    internal val keyPair: StoredKeyPair,
    internal val previousKeyPair: StoredKeyPair?,
    internal val previousIdentity: String?,
) {
    val publicKey: PublicKey = PublicKey(
        key_type = KeyType.fromValue(keyPair.keyType) ?: KeyType.KEY_TYPE_UNSPECIFIED,
        key = keyPair.publicKey.toByteString(),
    )
}

/**
 * We string-match the error message provided by the polycentric SDK to detect that a session has
 * expired. TODO: This will eventually be refactored into exception type matching.
 */
fun isPairingExpiredError(e: Throwable): Boolean =
    generateSequence(e) { it.cause }.any { it.message?.contains(PAIRING_EXPIRED_MESSAGE) == true }

private const val PAIRING_EXPIRED_MESSAGE = "pairing session has expired"

/**
 * Whether this device can approve other devices. Only rotation keys can change the identity
 * document, so a device that was paired as a non-rotation key (signing key) can't.
 */
suspend fun PolycentricAdapter.canIssuePairing(): Boolean {
    val identity = client.activeIdentityKey ?: return false
    return client.identityManager.isRotationKeyForIdentity(identity, client.currentSystem)
}

/** Starts a pairing session for the active identity on its first server. */
suspend fun PolycentricAdapter.startIssuerSession(): PairingSessionManager.PairingSession {
    requireActiveIdentity()
    val server = client.servers.firstOrNull() ?: throw IllegalStateException("No Harbor servers configured")
    return client.pairingSessionManager.createPairingSession(server)
}

/** The keys that have asked to join [info]'s session. */
suspend fun PolycentricAdapter.pollClaimers(info: PairingInfo): List<PublicKey> =
    client.pairingSessionManager.pollForClaimers(info)

/**
 * Authorizes [key] on the active identity, then updates the session state such that the claimer
 * sees the approval.
 */
suspend fun PolycentricAdapter.approveClaimer(
    session: PairingSessionManager.PairingSession,
    key: PublicKey,
    asRotationKey: Boolean,
) {
    client.sync(SyncStrategy.PARTIAL_PULL)

    val state = client.identityManager.getCurrent()
    val alreadyAuthorized = (state.rotationKeys + state.signingKeys).any { IdentityManager.keysEqual(it, key) }
    if (!alreadyAuthorized) {
        if (asRotationKey) {
            client.identityManager.addRotationKey(key)
        } else {
            client.identityManager.addSigningKey(key)
        }
    }

    // Update the session: the claimer checks the session state, not the identity on the server.
    client.pairingSessionManager.updatePairingSession(
        session.pairingInfo.server,
        session.digestBytes,
        session.issuerState.sequence + 1,
    )
}

/** Fetches the session behind [info] and the issuer's profile. */
suspend fun PolycentricAdapter.previewPairing(info: PairingInfo): PairingPreview {
    val session = client.pairingSessionManager.getPairingSession(info)
    val identity = session.digest.issuer_identity
    if (identity in listIdentities()) {
        throw PairingIdentityExistsException()
    }

    // The profile is only decoration, so show the bare key if it can't be fetched.
    val profile = try {
        getIdentityProfile(identity)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Logger.w(TAG, "Failed to fetch the issuer's profile.", e)
        null
    }
    return PairingPreview(session, profile)
}

/**
 * Creates a new key pair for a claimer device, and stores the previous keypair/identity in case of
 * cancellation.
 */
suspend fun PolycentricAdapter.prepareClaimerKey(): PendingClaim {
    val previousKeyPair = client.currentKeyPair
    val previousIdentity = client.activeIdentityKey
    val keyPair = client.keyPairManager.createKeyPair(setAsCurrent = true)
    return PendingClaim(keyPair, previousKeyPair, previousIdentity)
}

/** Registers the current key with [info]'s session. */
suspend fun PolycentricAdapter.joinPairing(info: PairingInfo) =
    client.pairingSessionManager.joinPairingSession(info)

/** Whether the issuer has authorized the the pairing. */
suspend fun PolycentricAdapter.pollAuthorization(info: PairingInfo): Boolean =
    client.pairingSessionManager.pollForAuthorization(info)

/**
 * Completes the pairing process on the claimer once authorization from the issuer has been
 * recieved. See [cancelClaimer] for when a pairing session is cancelled.
 */
suspend fun PolycentricAdapter.claimPaired(pending: PendingClaim, session: PairingSessionManager.PairingSession) {
    if (!pending.keyPair.publicKey.contentEquals(client.currentKeyPair?.publicKey)) {
        throw IllegalStateException("The key pair changed during pairing")
    }

    // Something else signed in while the user was pairing.
    if (client.activeIdentityKey != null) {
        throw IllegalStateException("Another identity became active during pairing")
    }

    val identity = session.digest.issuer_identity
    if (identity in listIdentities()) {
        throw PairingIdentityExistsException()
    }
    client.identityManager.claim(identity, serversForClaim(session, client.servers))
    saveClaimedIdentityChain(identity)
}

/**
 * Restores the key pair and identity that were active before the now-cancelled pending claim, and
 * deletes the pending claim info.
 */
suspend fun PolycentricAdapter.cancelClaimer(pending: PendingClaim) {
    if (client.currentKeyPair?.publicKey.contentEquals(pending.keyPair.publicKey)) {
        val previousKeyPair = pending.previousKeyPair ?: return
        client.setCurrentKeyPair(previousKeyPair)
        // setCurrentKeyPair signs in to the key's identity, so undo that if we were logged out.
        if (pending.previousIdentity == null && client.activeIdentityKey != null) {
            client.setActiveIdentityKey(null)
        }
    }
    client.keyPairManager.removeKeyPair(pending.publicKey)
}

/**
 * Servers to claim from: those from the pairing session, or another set held by the client.
 */
private fun serversForClaim(session: PairingSessionManager.PairingSession, currentServers: List<String>): List<String> {
    val bundle = session.issuerState.identity_state
        ?: throw IllegalStateException("Pairing session has no issuer identity state")
    val contentBytes = bundle.serialized_content?.content_bytes
        ?: throw IllegalStateException("Pairing session issuer identity state is invalid")
    val identity = Content.ADAPTER.decode(contentBytes).identity
        ?: throw IllegalStateException("Pairing session issuer identity state is invalid")

    identity.servers?.let { return it.urls }
    return (currentServers + session.pairingInfo.server).distinct()
}

private const val TAG = "PolycentricPairing"
