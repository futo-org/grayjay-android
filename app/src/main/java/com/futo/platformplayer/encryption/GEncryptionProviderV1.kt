package com.futo.platformplayer.encryption

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.Key
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class GEncryptionProviderV1 {
    private val _keyStore: KeyStore;
    private val secretKey: Key? get() = _keyStore.getKey(KEY_ALIAS, null);

    constructor() {
        _keyStore = KeyStore.getInstance(AndroidKeyStore);
        _keyStore.load(null);

        if (!_keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator: KeyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
            keyGenerator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(false)
                .build());

            keyGenerator.generateKey();
        }
    }

    fun encrypt(decrypted: String): String {
        val encrypted = encrypt(decrypted.toByteArray());
        val encoded = Base64.encodeToString(encrypted, Base64.DEFAULT);
        return encoded;
    }
    fun encrypt(decrypted: ByteArray): ByteArray {
        val ivBytes = generateIv()
        val c: Cipher = Cipher.getInstance(AES_MODE);
        c.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, ivBytes));
        val encodedBytes: ByteArray = c.doFinal(decrypted);
        return ivBytes + encodedBytes;
    }

    fun decrypt(data: String): String {
        val bytes = Base64.decode(data, Base64.DEFAULT)
        return String(decrypt(bytes));
    }
    fun decrypt(bytes: ByteArray): ByteArray {
        val encrypted = bytes.sliceArray(IntRange(IV_SIZE, bytes.size - 1))
        val ivBytes = bytes.sliceArray(IntRange(0, IV_SIZE - 1))

        val c = Cipher.getInstance(AES_MODE);
        c.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, ivBytes));
        return c.doFinal(encrypted);
    }

    private fun generateIv(): ByteArray {
        val r = SecureRandom()
        val ivBytes = ByteArray(IV_SIZE)
        r.nextBytes(ivBytes)
        return ivBytes
    }

    companion object {
        val instance: GEncryptionProviderV1 = GEncryptionProviderV1();

        private const val AndroidKeyStore = "AndroidKeyStore";
        private const val KEY_ALIAS = "FUTOMedia_Key";
        private const val AES_MODE = "AES/GCM/NoPadding";
        private const val IV_SIZE = 12;
        private const val TAG_LENGTH = 128
        private val TAG = "GEncryptionProviderV1";
    }
}