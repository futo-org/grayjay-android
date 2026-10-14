package com.futo.platformplayer.polycentric

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringStorage
import com.futo.polycentric.core.PEncryptionProviderV0
import com.futo.polycentric.core.PEncryptionProviderV1
import com.futo.platformplayer.toHexString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import org.futo.polycentric.core.KeyTypes
import org.futo.polycentric.core.StoredKeyPair
import org.futo.polycentric.core.SyncStrategy
import polycentric.v2.KeyType
import polycentric.v2.PublicKey

/*
 * Polycentric v1 to v2 identity migration. Harbor's Grayjay migration service authors a v2 identity
 * for every legacy v1 system, with the legacy system key as one of its rotation keys. On startup,
 * StatePolycentric.kt handles migrating each key using functions defined here.
 */

private const val TAG = "PolycentricMigration"

class MigratorLookupException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
private class MigratorLookupResponse(
    @SerialName("legacy_system_key") val legacySystemKey: String,
    val identity: String,
)

private val LOOKUP_JSON = Json { ignoreUnknownKeys = true }
private val IDENTITY_REGEX = Regex("^[0-9a-f]{64}$")

private const val LEGACY_DATABASE_NAME = "polycentric_core.db"

/**
 * Import of the keys in the v1 Polycentric SDK database into v2 [PolycentricKeyStorage]. The v1
 * SDK kept keys both in the database and Grayjay's FragmentedStorage; see [PolycentricKeyStorage]
 * for how we access FragmentedStorage keys.
 */
fun importLegacyDatabaseKeys(context: Context, storage: PolycentricKeyStorage = PolycentricKeyStorage.instance) {
    val imported = FragmentedStorage.get<StringStorage>("polycentricLegacyDatabaseImported")
    if (imported.value.isNotEmpty()) return

    try {
        val file = context.getDatabasePath(LEGACY_DATABASE_NAME)
        var added = 0
        if (file.exists()) {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT value FROM process_secrets", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val value = cursor.getBlob(0) ?: continue
                        try {
                            if (storage.importProcessSecret(decryptLegacySecret(value))) added++
                        } catch (e: Throwable) {
                            Logger.w(TAG, "Failed to import legacy database key.", e)
                        }
                    }
                }
            }
        }
        imported.setAndSaveBlocking("true")
        Logger.i(TAG, "Legacy database key import finished: added $added")
    } catch (e: Throwable) {
        // Retried on the next start
        Logger.e(TAG, "Failed to read legacy database keys.", e)
    }
}

/** Decrypts with [PEncryptionProviderV1], or [PEncryptionProviderV0] as fallback. */
private fun decryptLegacySecret(value: ByteArray): ByteArray =
    try {
        PEncryptionProviderV1.instance.decrypt(value)
    } catch (e: Throwable) {
        PEncryptionProviderV0.instance.decrypt(value)
    }

/**
 * Look up the v2 identity the migrator created for a legacy system key: null when the migrator
 * has none, throws [MigratorLookupException] on any failure.
 */
suspend fun lookupMigratedIdentity(baseUrl: String, publicKey: ByteArray): String? = withContext(Dispatchers.IO) {
    val keyHex = publicKey.toHexString()
    val client = ManagedHttpClient().apply {
        setTimeout(5_000)
        setReadTimeout(10_000)
        setCallTimeout(15_000)
    }
    val url = "${baseUrl.trimEnd('/')}/identity/$keyHex"
    val start = System.currentTimeMillis()
    try {
        client.get(url).use { response ->
            val body = response.body?.string()
            Logger.i(TAG, "Migrator lookup GET $url -> HTTP ${response.code} in ${System.currentTimeMillis() - start}ms, body: ${body?.take(500)}")
            parseLookupResponse(response.code, body, keyHex)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: MigratorLookupException) {
        Logger.w(TAG, "Migrator lookup for $keyHex rejected: ${e.message}", e)
        throw e
    } catch (e: Throwable) {
        Logger.w(TAG, "Migrator lookup GET $url failed after ${System.currentTimeMillis() - start}ms", e)
        throw MigratorLookupException("Migrator lookup failed", e)
    }
}

internal fun parseLookupResponse(code: Int, body: String?, requestedKeyHex: String): String? {
    if (code == 404) {
        return null
    }
    if (code !in 200..299) {
        throw MigratorLookupException("Migrator lookup returned HTTP $code")
    }
    val response = try {
        LOOKUP_JSON.decodeFromString<MigratorLookupResponse>(body ?: "")
    } catch (e: Throwable) {
        throw MigratorLookupException("Malformed migrator response", e)
    }
    if (response.legacySystemKey != requestedKeyHex) {
        throw MigratorLookupException("Migrator response is for a different key")
    }
    if (!IDENTITY_REGEX.matches(response.identity)) {
        throw MigratorLookupException("Migrator returned a malformed identity")
    }
    return response.identity
}

/**
 * Unbound Ed25519 keypairs stored on this device. On a device that used v1 these are v1 system
 * keys; keys created through v2 are bound as soon as their identity is published.
 */
suspend fun PolycentricAdapter.legacyKeyPairs(): List<StoredKeyPair> =
    client.keyPairManager.getKeys().filter {
        it.keyType == KeyTypes.ED25519 && client.getIdentityKeyFor(it) == null
    }

/**
 * Claim the identity that Harbor's Grayjay migrator created for a legacy v1 system key [keyPair].
 * Ensures that the previously active identity before the claim remains active after claiming.
 * Assumes the legacy key is set as an authorized rotation key of the v2 identity. Throws on a
 * failure of the claim process.
 */
suspend fun PolycentricAdapter.claimMigratedIdentity(keyPair: StoredKeyPair, identity: String) {
    val priorKeyPair = client.currentKeyPair
    val priorIdentity = client.activeIdentityKey
    val keyHex = keyPair.publicKey.toHexString()
    Logger.i(TAG, "Claiming $identity with legacy key $keyHex (prior key ${priorKeyPair?.publicKey?.toHexString()}, prior identity $priorIdentity)")
    try {
        // Fetch the identity from an arbitrary server to start
        client.adoptServers((PolycentricAdapter.SEED_SERVERS + client.servers).distinct())
        Logger.i(TAG, "Claim servers: ${client.servers}")
        client.setCurrentKeyPair(keyPair)
        // When we claim, we adopt the identity's servers
        val state = client.identityManager.claim(identity)

        // TODO remove upon release: switch to staging servers for testing (the migrator service
        // sets migrated identities' servers to production)
        client.adoptServers(PolycentricAdapter.SEED_SERVERS)
        client.sync(SyncStrategy.PARTIAL_PUSH)
        client.identityManager.publish(identity, state.rotationKeys, state.signingKeys, PolycentricAdapter.SEED_SERVERS)

        Logger.i(TAG, "Claimed $identity: rotation keys ${state.rotationKeys.map { it.key.hex() }}, " +
            "signing keys ${state.signingKeys.map { it.key.hex() }}, servers ${state.servers}")
    } catch (e: Throwable) {
        val boundTo = runCatching { client.getIdentityKeyFor(keyPair) }.getOrNull()
        Logger.w(TAG, "Claim of $identity with legacy key $keyHex failed (${e.javaClass.name}: ${e.message}); key now bound to $boundTo", e)
        throw e
    } finally {
        if (priorKeyPair != null) {
            client.setCurrentKeyPair(priorKeyPair)
            if (priorIdentity == null) {
                client.setActiveIdentityKey(null)
            }
        }
    }
}

/** Delete a legacy keypair from this device, abandoning its migration. */
suspend fun PolycentricAdapter.deleteLegacyKeyPair(keyPair: StoredKeyPair) =
    client.keyPairManager.removeKeyPair(
        PublicKey(key_type = KeyType.KEY_TYPE_ED25519, key = keyPair.publicKey.toByteString())
    )
