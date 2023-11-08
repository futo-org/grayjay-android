package com.futo.platformplayer.encryption

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.futo.polycentric.core.EncryptionProvider
import java.security.Key
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptionProvider {
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
        val encodedBytes = encrypt(decrypted.toByteArray());
        val encrypted = Base64.encodeToString(encodedBytes, Base64.DEFAULT);
        return encrypted;
    }
    fun encrypt(decrypted: ByteArray): ByteArray {
        val c: Cipher = Cipher.getInstance(AES_MODE);
        c.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, FIXED_IV));
        val encodedBytes: ByteArray = c.doFinal(decrypted);
        return encodedBytes;
    }

    fun decrypt(encrypted: String): String {
        val c = Cipher.getInstance(AES_MODE);
        c.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, FIXED_IV));
        val decrypted = String(c.doFinal(Base64.decode(encrypted, Base64.DEFAULT)));
        return decrypted;
    }
    fun decrypt(encrypted: ByteArray): ByteArray {
        val c = Cipher.getInstance(AES_MODE);
        c.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, FIXED_IV));
        return c.doFinal(encrypted);
    }

    companion object {
        val instance: EncryptionProvider = EncryptionProvider();

        private val FIXED_IV = byteArrayOf(12, 43, 127, 2, 99, 22, 6,  78,  24, 53, 8, 101);
        private const val AndroidKeyStore = "AndroidKeyStore";
        private const val KEY_ALIAS = "FUTOMedia_Key";
        private const val AES_MODE = "AES/GCM/NoPadding";
        private val TAG = "EncryptionProvider";
    }
}