package com.futo.platformplayer.polycentric

import com.futo.platformplayer.encryption.GEncryptionProviderV1
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.StringArrayStorage
import java.security.SecureRandom
import com.google.protobuf.ByteString

/**
 * Stores signing keys using [FragmentedStorage] such that keys are kept
 * encrypted at rest. Keys are kept in a backwards-compatible format with
 * Polycentric v1 keys, such that migration to Polycentric v2 is seamless
 * with app users.
 *
 * Note that keys in v1 contained the `Process` field, which is not read
 * in v2.
 */
class PolycentricKeyStorage {
    private val _processSecrets = FragmentedStorage.get<StringArrayStorage>("processSecrets")

    class ProcessSecret(val publicKey: ByteArray, val privateKey: ByteArray)

    fun addProcessSecret(publicKey: ByteArray, privateKey: ByteArray) {
        val process = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val encoded = StorageTypeProcessSecret.newBuilder()
            .setSystem(PrivateKey.newBuilder()
                .setKeyType(KEY_TYPE_ED25519)
                .setKey(ByteString.copyFrom(privateKey)))
            .setProcess(Process.newBuilder().setProcess(ByteString.copyFrom(process)))
            .build()
            .toByteArray()
        _processSecrets.addDistinct(GEncryptionProviderV1.instance.encrypt(encoded).toBase64())
        _processSecrets.saveBlocking()
    }

    fun getProcessSecrets(): List<ProcessSecret> {
        val secrets = arrayListOf<ProcessSecret>()
        for (p in _processSecrets.getAllValues()) {
            try {
                val secret = StorageTypeProcessSecret.parseFrom(
                    GEncryptionProviderV1.instance.decrypt(p.base64ToByteArray()),
                )
                val key = secret.system.key
                if (secret.system.keyType != KEY_TYPE_ED25519 || key.size() != 32) continue
                secrets.add(ProcessSecret(publicKey = deriveEd25519PublicKey(key.toByteArray()), privateKey = key.toByteArray()))
            } catch (e: Throwable) {
                Logger.i(TAG, "Failed to decrypt process secret", e)
            }
        }
        return secrets
    }

    fun removeProcessSecret(publicKey: ByteArray) {
        for (p in _processSecrets.getAllValues()) {
            try {
                val secret = StorageTypeProcessSecret.parseFrom(
                    GEncryptionProviderV1.instance.decrypt(p.base64ToByteArray()),
                )
                val key = secret.system.key
                if (secret.system.keyType == KEY_TYPE_ED25519 && key.toByteArray().contentEquals(publicKey))
                    _processSecrets.remove(p)
            } catch (e: Throwable) {
                Logger.i(TAG, "Failed to decrypt process secret", e)
            }
        }
        _processSecrets.saveBlocking()
    }

		// Singleton instance of the PolycentricKeyStorage object
    companion object {
        const val KEY_TYPE_ED25519 = 1L
        val TAG = "PolycentricKeyStorage"
        private var _instance: PolycentricKeyStorage? = null
        val instance: PolycentricKeyStorage
            get() {
                if (_instance == null)
                    _instance = PolycentricKeyStorage()
                return _instance!!
            }

        /** Ed25519 public key (32 bytes) derived from a 32-byte seed. */
        internal fun deriveEd25519PublicKey(seed: ByteArray): ByteArray {
            val params = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(seed, 0)
            return params.generatePublicKey().encoded
        }

        private fun ByteArray.toBase64(): String =
            android.util.Base64.encodeToString(this, android.util.Base64.DEFAULT)

        private fun String.base64ToByteArray(): ByteArray =
            android.util.Base64.decode(this, android.util.Base64.DEFAULT)
    }
}
