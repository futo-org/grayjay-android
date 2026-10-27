package com.futo.platformplayer.polycentric

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringArrayStorage
import com.futo.platformplayer.stores.StringStorage
import com.futo.platformplayer.toHexString
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import org.futo.polycentric.core.Collections
import org.futo.polycentric.core.KeyTypes
import org.futo.polycentric.core.StoredKeyPair
import polycentric.v2.KeyType
import polycentric.v2.PublicKey
import java.io.IOException
import java.security.Key
import java.security.KeyStore
import java.util.Base64
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/*
 * Polycentric v1 to v2 identity migration. Harbor's Grayjay migration service authors a v2 identity
 * for every legacy v1 system, with the legacy system key as one of its rotation keys. On startup,
 * StatePolycentric.kt handles migrating each key using functions defined here.
 */

private const val TAG = "PolycentricMigration"

class MigratorLookupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The legacy key has already claimed its migrated identity on another device. Harbor expects a key
 * per device, so this device should be paired instead.
 */
class LegacyKeyAlreadyClaimedException(val identity: String) :
    Exception("Legacy key has already claimed identity $identity")

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
 * Assumes the legacy key is set as an authorized rotation key of the v2 identity. Does nothing if
 * the key is already bound to [identity]. Throws on a failure of the claim process.
 */
suspend fun PolycentricAdapter.claimMigratedIdentity(keyPair: StoredKeyPair, identity: String) {
    val keyHex = keyPair.publicKey.toHexString()
    if (client.getIdentityKeyFor(keyPair) == identity) {
        Logger.i(TAG, "Legacy key $keyHex already claimed $identity on this device")
        return
    }

    val priorKeyPair = client.currentKeyPair
    val priorIdentity = client.activeIdentityKey
    Logger.i(TAG, "Claiming $identity with legacy key $keyHex (prior key ${priorKeyPair?.publicKey?.toHexString()}, prior identity $priorIdentity)")
    try {
        // Fetch the identity from an arbitrary server to start
        client.adoptServers((PolycentricAdapter.SEED_SERVERS + client.servers).distinct())
        Logger.i(TAG, "Claim servers: ${client.servers}")
        // Any identity event signed by the legacy key is a claim from a device that already
        // migrated. Claiming again would sign events with the same key from two devices, which we
        // want to avoid.
        if (isLegacyKeyClaimed(keyPair, identity)) {
            Logger.w(TAG, "Legacy key $keyHex has already claimed $identity; not claiming again")
            throw LegacyKeyAlreadyClaimedException(identity)
        }
        client.setCurrentKeyPair(keyPair)
        // When we claim, we adopt the identity's servers
        val state = client.identityManager.claim(identity)
        saveClaimedIdentityChain(identity)

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

/**
 * Whether the identity chain has an identity event signed by [keyPair]. A claim republishes the
 * identity head with the claiming key to the identity's own servers, which can differ from the
 * seed servers the migrator created the identity on, so both are checked.
 */
private suspend fun PolycentricAdapter.isLegacyKeyClaimed(keyPair: StoredKeyPair, identity: String): Boolean {
    val servers = client.servers
    try {
        val identityServers = client.identityManager.fetchIdentityState(identity)?.servers.orEmpty()
        client.adoptServers((servers + identityServers).distinct())
        val claims = client.listEvents(
            identity = identity,
            collection = Collections.IDENTITY,
            limit = 1,
            signedBy = keyPair.toPublicKey(),
        )
        Logger.i(TAG, "Legacy key claim check of $identity on ${client.servers}: ${claims.size} claims")
        return claims.isNotEmpty()
    } finally {
        client.adoptServers(servers)
    }
}

/**
 * Stops tracking [keyPair] as a legacy key needing migration, but does not delete the key.
 */
fun markLegacyKeyMigrated(keyPair: StoredKeyPair) {
    legacyKeys.remove(keyPair.publicKey.toHexString())
    legacyKeys.saveBlocking()
}

class LegacyExportParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

private const val LEGACY_EXPORT_PREFIX = "polycentric://"
private const val LEGACY_URL_TYPE_EXPORT_BUNDLE = 3L

/**
 * Parses a v1 profile export (a `polycentric://` link, optionally gzipped by v1 for large QR
 * codes) into its Ed25519 seed. Throws [LegacyExportParseException] on invalid exports.
 */
fun parseLegacyExport(text: String): ByteArray {
    val trimmed = text.trim()
    if (!trimmed.startsWith(LEGACY_EXPORT_PREFIX)) {
        throw LegacyExportParseException("Not a Polycentric profile export")
    }

    var data = try {
        Base64.getUrlDecoder().decode(trimmed.substring(LEGACY_EXPORT_PREFIX.length).filterNot { it.isWhitespace() })
    } catch (e: IllegalArgumentException) {
        throw LegacyExportParseException("Polycentric profile export is not valid base64", e)
    }

    // Check for gzip
    if (data.size >= 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()) {
        data = try {
            GZIPInputStream(data.inputStream()).use { it.readBytes() }
        } catch (e: IOException) {
            throw LegacyExportParseException("Polycentric profile export could not be decompressed", e)
        }
    }

    val keyPair = try {
        val urlInfo = LegacyURLInfo.parseFrom(data)
        if (urlInfo.urlType != LEGACY_URL_TYPE_EXPORT_BUNDLE) {
            throw LegacyExportParseException("Polycentric link is not a profile export")
        }
        LegacyExportBundle.parseFrom(urlInfo.body).keyPair
    } catch (e: InvalidProtocolBufferException) {
        throw LegacyExportParseException("Polycentric profile export is malformed", e)
    }

    val seed = keyPair.privateKey.toByteArray()
    if (keyPair.keyType != PolycentricKeyStorage.KEY_TYPE_ED25519 || seed.size != 32) {
        throw LegacyExportParseException("Polycentric profile export has an unsupported key")
    }

    val publicKey = keyPair.publicKey.toByteArray()
    if (publicKey.isNotEmpty() && !PolycentricKeyStorage.deriveEd25519PublicKey(seed).contentEquals(publicKey)) {
        throw LegacyExportParseException("Polycentric profile export has mismatched keys")
    }

    return seed
}

/**
 * Stores the v1 key with [seed] on this device and records it for migration. Returns whether the
 * key was newly added, such that a failed import can be undone with [forgetLegacyKey].
 */
fun importLegacyKey(seed: ByteArray, storage: PolycentricKeyStorage = PolycentricKeyStorage.instance): Boolean {
    val publicKey = PolycentricKeyStorage.deriveEd25519PublicKey(seed)
    val added = storage.getProcessSecrets().none { it.publicKey.contentEquals(publicKey) }
    if (added) {
        storage.addProcessSecret(publicKey, seed)
    }
    addLegacyKeys(listOf(publicKey))
    Logger.i(TAG, "Imported legacy key ${publicKey.toHexString()} (added $added)")
    return added
}

/** Removes the v1 key [publicKey] from this device and stops tracking it for migration. */
fun forgetLegacyKey(publicKey: ByteArray, storage: PolycentricKeyStorage = PolycentricKeyStorage.instance) {
    storage.removeProcessSecret(publicKey)
    legacyKeys.remove(publicKey.toHexString())
    legacyKeys.saveBlocking()
}

/** The stored keypair for [publicKey] and the identity it is bound to, if any. */
suspend fun PolycentricAdapter.findKeyPair(publicKey: ByteArray): Pair<StoredKeyPair, String?>? {
    val keyPair = client.keyPairManager.getKeys().firstOrNull { it.publicKey.contentEquals(publicKey) } ?: return null
    return keyPair to client.getIdentityKeyFor(keyPair)
}

private fun StoredKeyPair.toPublicKey() =
    PublicKey(key_type = KeyType.KEY_TYPE_ED25519, key = publicKey.toByteString())
