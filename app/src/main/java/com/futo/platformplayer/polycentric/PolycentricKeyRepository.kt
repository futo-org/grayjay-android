package com.futo.platformplayer.polycentric

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.polycentric.core.IKeysRepository
import org.futo.polycentric.core.StoredKeyPair

/**
 * Wraps [PolycentricKeyStorage] to implement [IKeysRepository] from Polycentric core.
 */
class PolycentricKeyRepository(private val storage: PolycentricKeyStorage = PolycentricKeyStorage.instance) :
    IKeysRepository {

    override suspend fun save(publicKey: ByteArray, keyType: Int, privateKey: ByteArray) =
        withContext(Dispatchers.IO) {
            if (keyType != KEY_TYPE_ED25519_INT)
                throw IllegalArgumentException("Unsupported key type $keyType")
            if (privateKey.size != 32)
                throw IllegalArgumentException("Ed25519 seed must be 32 bytes")
            storage.addProcessSecret(publicKey, privateKey)
            Unit
        }

    override suspend fun getAll(): List<StoredKeyPair> = withContext(Dispatchers.IO) {
        storage.getProcessSecrets().map { it.toStoredKeyPair() }
    }

    override suspend fun getByPublicKey(publicKey: ByteArray): StoredKeyPair? =
        withContext(Dispatchers.IO) {
            storage.getProcessSecrets()
                .firstOrNull { it.publicKey.contentEquals(publicKey) }
                ?.toStoredKeyPair()
        }

    override suspend fun delete(publicKey: ByteArray): Unit = withContext(Dispatchers.IO) {
        storage.removeProcessSecret(publicKey)
    }

    private fun PolycentricKeyStorage.ProcessSecret.toStoredKeyPair() =
        StoredKeyPair(keyType = KEY_TYPE_ED25519_INT, publicKey = publicKey, privateKey = privateKey)

    companion object {
        const val KEY_TYPE_ED25519_INT = 1
    }
}
