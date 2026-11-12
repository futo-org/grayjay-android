package com.futo.platformplayer.polycentric

import com.futo.platformplayer.logging.Logger
import kotlinx.coroutines.CancellationException
import org.futo.polycentric.core.Collections
import org.futo.polycentric.core.getProfile
import polycentric.v2.Content
import polycentric.v2.Image
import polycentric.v2.ImageSet
import polycentric.v2.ProfileUpdate

/**
 * [PolycentricAdapter] functions for identity and profile lifecycle,
 * including server configuration.
 */

/** An avatar image variant at a specific size, stored as a byte array */
class AvatarVariant(val bytes: ByteArray, val width: Int, val height: Int)

/**
 * The public profile of a Harbor/Polycentric identity.
 */
class PolycentricChannelProfile(
    val identity: String,
    val name: String?,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val description: String?,
)

/** Whether an identity is bound to the current keypair, meaning the user is logged in. */
val PolycentricAdapter.isLoggedIn: Boolean
    get() = client.activeIdentityKey != null

/** The active identity key, or null when logged out. */
fun PolycentricAdapter.activeIdentity(): String? = client.activeIdentityKey

/** Create a new identity, optionally naming it, and log in to it. */
suspend fun PolycentricAdapter.createIdentity(name: String? = null): String {
    client.keyPairManager.createKeyPair(setAsCurrent = true)

    // IdentityManager.publish also binds the identity to the current keypair
    // (setActiveIdentityKey), which is what makes it appear in listIdentities().
    val identityKey = client.identityManager.publish(
        identityKey = null,
        rotationKeys = listOf(client.currentSystem),
        signingKeys = emptyList(),
        servers = client.servers,
    ).identityKey

    if (!name.isNullOrBlank()) {
        publish(Content(profile_update = ProfileUpdate(name = name)), Collections.PROFILE)
    }
    return identityKey
}

/** Log out by unsetting the active identity, but not deleting any kept identities. */
suspend fun PolycentricAdapter.logout() = client.setActiveIdentityKey(null)

/** All identity keys bound to locally stored keypairs. */
suspend fun PolycentricAdapter.listIdentities(): List<String> =
    client.keyPairManager.getKeys().mapNotNull { client.getIdentityKeyFor(it) }.distinct()

/** Log in as the given identity. The identity must be bound to a stored keypair. */
suspend fun PolycentricAdapter.switchIdentity(identityKey: String) {
    val keyPair = client.keyPairManager.getKeys()
        .firstOrNull { client.getIdentityKeyFor(it) == identityKey }
        ?: throw IllegalArgumentException("No stored keypair is bound to identity $identityKey")
    client.setCurrentKeyPair(keyPair)
}

/**
 * Delete the active identity from this device: only the local keypair is removed, the identity
 * document itself stays on its servers.
 */
suspend fun PolycentricAdapter.deleteActiveIdentity() {
    client.activeIdentityKey
        ?: throw IllegalStateException("Not logged in to Harbor")
    client.keyPairManager.removeKeyPair(client.currentSystem)
    client.setActiveIdentityKey(null)
}

/**
 * Resolve the profile of an identity. Falls back to locally stored profile events when the
 * network fetch fails or yields nothing.
 */
suspend fun PolycentricAdapter.getIdentityProfile(identity: String): PolycentricChannelProfile {
    val remote = try {
        client.getProfile(identity)?.event_bundles
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Logger.w(TAG, "Failed to fetch profile, falling back to local events.", e)
        null
    }
    val bundles = remote?.takeIf { it.isNotEmpty() }
        ?: client.listValidEvents(identity, Collections.PROFILE)
    val update = latestProfileUpdate(bundles)
    return PolycentricChannelProfile(
        identity = identity,
        name = update?.name,
        avatarUrl = bestBlobUrl(update?.avatar),
        bannerUrl = bestBlobUrl(update?.banner),
        description = update?.description,
    )
}

/**
 * Update the active identity's profile. Note that updates happen by full replacement (if an
 * argument is not passed it will be replaced with its own value).
 */
suspend fun PolycentricAdapter.updateProfile(
    name: String,
    avatar: List<AvatarVariant>? = null,
    mimeType: String = "image/png",
) {
    val identity = requireActiveIdentity()
    val current = latestProfileUpdate(client.listValidEvents(identity, Collections.PROFILE))

    val avatarSet = avatar?.takeIf { it.isNotEmpty() }?.let { variants ->
        ImageSet(variants.map { variant ->
            val blob = client.commitBlob(variant.bytes, mimeType)
            client.uploadBlob(blob, variant.bytes)
            Image(
                blob = blob,
                width = variant.width,
                height = variant.height,
            )
        })
    }

    publish(
        Content(profile_update = ProfileUpdate(
            name = name,
            avatar = avatarSet ?: current?.avatar,
            banner = current?.banner,
            description = current?.description,
            alias = current?.alias,
        )),
        Collections.PROFILE,
    )
}

/**
 * Returns either the servers the identity declares, or the client's current servers if the identity
 * hasn't set any.
 */
internal fun effectiveServers(documentServers: List<String>?, clientServers: List<String>): List<String> =
    documentServers ?: clientServers

/** The server URL to add for user input [url]. */
internal fun serverUrlFromInput(url: String): String = url.trim()

/** The active identity's servers, in identity document order. */
suspend fun PolycentricAdapter.getServers(): List<String> =
    effectiveServers(client.identityManager.getCurrent().servers, client.servers)

/**
 * Whether the current keypair is a rotation key of the active identity, which is required to
 * publish a changed server list.
 */
suspend fun PolycentricAdapter.canEditServers(): Boolean {
    val identityKey = client.activeIdentityKey ?: return false
    return client.identityManager.isRotationKeyForIdentity(identityKey, client.currentSystem)
}

/**
 * Add a server to the end of the active identity's server list. Throws
 * [org.futo.polycentric.core.ServerAlreadyAddedException] when it is already listed, and fails
 * when the server is unreachable.
 */
suspend fun PolycentricAdapter.addServer(url: String) {
    client.identityManager.addServer(serverUrlFromInput(url))
}

/** Remove a server from the active identity's server list. */
suspend fun PolycentricAdapter.removeServer(url: String) {
    client.identityManager.removeServer(url)
}

private const val TAG = "PolycentricIdentity"
