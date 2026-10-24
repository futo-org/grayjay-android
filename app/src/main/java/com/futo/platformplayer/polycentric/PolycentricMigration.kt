package com.futo.platformplayer.polycentric

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringArrayStorage
import com.futo.platformplayer.stores.StringStorage
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
import polycentric.v2.KeyType
import polycentric.v2.PublicKey
import java.security.Key
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

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

/** v1 public keys on this device that should be migrated. */
private val legacyKeys get() = FragmentedStorage.get<StringArrayStorage>("polycentricLegacyKeys")

private fun addLegacyKeys(publicKeys: List<ByteArray>) {
    for (publicKey in publicKeys) {
        legacyKeys.addDistinct(publicKey.toHexString())
    }
    legacyKeys.saveBlocking()
}

/**
 * Records the v1 keys on this device for migration, pulled from [PolycentricKeyStorage] and the
 * Polycentric SDK v1 SQLite database. The record is used to distinguish between v1 keys that
 * haven't been migrated, and v2 keys that haven't been bound to an identity yet.
 */
fun recordLegacyKeys(context: Context, storage: PolycentricKeyStorage = PolycentricKeyStorage.instance) {
    val recorded = FragmentedStorage.get<StringStorage>("polycentricLegacyKeysRecorded")
    if (recorded.value.isEmpty()) {
        val keys = storage.getProcessSecrets().map { it.publicKey }
        addLegacyKeys(keys)
        recorded.setAndSaveBlocking("true")
        Logger.i(TAG, "Recorded legacy keys: ${keys.map { it.toHexString() }}")
    }
    importLegacyDatabaseKeys(context, storage)
}

/**
 * Import of the keys in the v1 Polycentric SDK database into v2 [PolycentricKeyStorage]. The v1
 * SDK kept keys both in the database and Grayjay's FragmentedStorage; see [PolycentricKeyStorage]
 * for how we access FragmentedStorage keys.
 */
private fun importLegacyDatabaseKeys(context: Context, storage: PolycentricKeyStorage) {
    val imported = FragmentedStorage.get<StringStorage>("polycentricLegacyDatabaseImported")
    if (imported.value.isNotEmpty()) return

    try {
        val file = context.getDatabasePath(LEGACY_DATABASE_NAME)
        val keys = mutableListOf<ByteArray>()
        if (file.exists()) {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT value FROM process_secrets", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val value = cursor.getBlob(0) ?: continue
                        try {
                            storage.importProcessSecret(decryptLegacySecret(value))?.let { keys.add(it) }
                        } catch (e: Throwable) {
                            Logger.w(TAG, "Failed to import legacy database key.", e)
                        }
                    }
                }
            }
        }
        addLegacyKeys(keys)
        imported.setAndSaveBlocking("true")
        Logger.i(TAG, "Legacy database key import finished: imported ${keys.map { it.toHexString() }}")
    } catch (e: Throwable) {
        // Retried on the next start
        Logger.e(TAG, "Failed to read legacy database keys.", e)
    }
}

private const val LEGACY_KEY_ALIAS = "PolycentricCore_Key"
private const val LEGACY_IV_SIZE = 12
private const val LEGACY_TAG_LENGTH = 128
private val LEGACY_V0_FIXED_IV = byteArrayOf(12, 43, 127, 2, 99, 22, 6, 78, 24, 53, 8, 101)

/**
 * Decrypts a secret written by the v1 SDK's AES-GCM Android Keystore encryption: the V1 format
 * (random IV prefixed to the ciphertext), or the V0 format (fixed IV) as fallback.
 */
private fun decryptLegacySecret(value: ByteArray): ByteArray {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val key = keyStore.getKey(LEGACY_KEY_ALIAS, null)
        ?: throw IllegalStateException("Legacy encryption key not found")
    return try {
        decryptLegacyAesGcm(key, value.copyOfRange(0, LEGACY_IV_SIZE), value.copyOfRange(LEGACY_IV_SIZE, value.size))
    } catch (e: Throwable) {
        decryptLegacyAesGcm(key, LEGACY_V0_FIXED_IV, value)
    }
}

private fun decryptLegacyAesGcm(key: Key, iv: ByteArray, encrypted: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(LEGACY_TAG_LENGTH, iv))
    return cipher.doFinal(encrypted)
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
 * v1 system keys stored on this device (see [recordLegacyKeys]) that are not yet bound to a v2
 * identity, i.e. not yet migrated.
 */
suspend fun PolycentricAdapter.legacyKeyPairs(): List<StoredKeyPair> {
    val legacy = legacyKeys.getAllValues().toSet()
    return client.keyPairManager.getKeys().filter {
        it.keyType == KeyTypes.ED25519 && it.publicKey.toHexString() in legacy && client.getIdentityKeyFor(it) == null
    }
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
suspend fun PolycentricAdapter.deleteLegacyKeyPair(keyPair: StoredKeyPair) {
    client.keyPairManager.removeKeyPair(
        PublicKey(key_type = KeyType.KEY_TYPE_ED25519, key = keyPair.publicKey.toByteString())
    )
    legacyKeys.remove(keyPair.publicKey.toHexString())
    legacyKeys.saveBlocking()
}
