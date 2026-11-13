package com.futo.platformplayer.polycentric

import android.content.Context
import com.futo.platformplayer.R
import com.futo.platformplayer.logging.Logger
import kotlinx.coroutines.CancellationException
import org.futo.polycentric.core.Collections
import org.futo.polycentric.core.getProfile
import org.futo.polycentric.ffi.common.ContentBodyValidationError
import org.futo.polycentric.ffi.common.ContentValidationError
import org.futo.polycentric.ffi.common.ProfileUpdateValidationError
import org.futo.polycentric.ffi.common.StringError
import org.futo.polycentric.ffi.validateContent
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
 * Resolve the profile of an identity. See [resolveProfileUpdate] for which event is used.
 */
suspend fun PolycentricAdapter.getIdentityProfile(identity: String): PolycentricChannelProfile {
    val update = resolveProfileUpdate(identity)
    return PolycentricChannelProfile(
        identity = identity,
        name = update?.name,
        avatarUrl = bestBlobUrl(update?.avatar),
        bannerUrl = bestBlobUrl(update?.banner),
        description = update?.description,
    )
}

/**
 * The newest profile update of [identity] that has content. Uses the server's latest profile
 * event; if the server dropped its content (it was invalid), the newest event on the server that
 * has content; and if the servers can't be reached, local events. Local events are always
 * included, so a profile update this device hasn't pushed yet is still considered.
 */
private suspend fun PolycentricAdapter.resolveProfileUpdate(identity: String): ProfileUpdate? {
    val remote = try {
        val latest = client.getProfile(identity)?.event_bundles.orEmpty()
        if (latest.isEmpty() || latestProfileUpdate(latest) != null) {
            latest
        } else {
            client.listEvents(identity, Collections.PROFILE)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Logger.w(TAG, "Failed to fetch profile, falling back to local events.", e)
        emptyList()
    }
    return latestProfileUpdate(remote + client.listValidEvents(identity, Collections.PROFILE))
}

/**
 * The SDK's validation error for [name] as a profile name, or null when it is valid. Lets the
 * profile screens re-prompt for the name before anything is published.
 */
fun profileNameError(name: String): StringError? =
    validateContent(Content.ADAPTER.encode(Content(profile_update = ProfileUpdate(name = name))))
        .firstNotNullOfOrNull { error ->
            val body = (error as? ContentValidationError.ContentBody)?.v1
            val update = (body as? ContentBodyValidationError.ProfileUpdate)?.v1
            (update as? ProfileUpdateValidationError.Name)?.v1
        }

/** Message to show the user for a [profileNameError]. */
fun profileNameErrorMessage(context: Context, error: StringError): String = context.getString(
    if (error is StringError.TooLong) R.string.name_is_too_long else R.string.name_is_invalid
)

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
    val current = resolveProfileUpdate(identity)

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

    // Fields carried over from the current profile can be invalid (e.g. a migrated v1 description
    // over the server's limit), which would fail the whole update. Grayjay can't edit them, so
    // leave those out; the name is checked by the profile screens instead.
    publish(
        Content(profile_update = ProfileUpdate(
            name = name,
            avatar = avatarSet ?: current?.avatar?.takeIf { isValidProfileUpdate(ProfileUpdate(avatar = it)) },
            banner = current?.banner?.takeIf { isValidProfileUpdate(ProfileUpdate(banner = it)) },
            description = current?.description?.takeIf { isValidProfileUpdate(ProfileUpdate(description = it)) },
            alias = current?.alias?.takeIf { isValidProfileUpdate(ProfileUpdate(alias = it)) },
        )),
        Collections.PROFILE,
    )
}

/** Whether [update] passes the SDK's content validation. */
private fun isValidProfileUpdate(update: ProfileUpdate): Boolean =
    validateContent(Content.ADAPTER.encode(Content(profile_update = update))).isEmpty()

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
