package com.futo.platformplayer.encryption

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class GPasswordEncryptionProviderV0 {
    private val _key: SecretKeySpec;

    constructor(password: String) {
        _key = SecretKeySpec(password.toByteArray(), "AES");
    }

    fun encrypt(decrypted: String): String {
        val encodedBytes = encrypt(decrypted.toByteArray());
        val encrypted = Base64.encodeToString(encodedBytes, Base64.DEFAULT);
        return encrypted;
    }
    fun encrypt(decrypted: ByteArray): ByteArray {
        val c: Cipher = Cipher.getInstance(AES_MODE);
        c.init(Cipher.ENCRYPT_MODE, _key, GCMParameterSpec(TAG_LENGTH, FIXED_IV));
        val encodedBytes: ByteArray = c.doFinal(decrypted);
        return encodedBytes;
    }

    fun decrypt(encrypted: String): String {
        val c = Cipher.getInstance(AES_MODE);
        c.init(Cipher.DECRYPT_MODE, _key, GCMParameterSpec(TAG_LENGTH, FIXED_IV));
        val decrypted = String(c.doFinal(Base64.decode(encrypted, Base64.DEFAULT)));
        return decrypted;
    }
    fun decrypt(encrypted: ByteArray): ByteArray {
        val c = Cipher.getInstance(AES_MODE);
        c.init(Cipher.DECRYPT_MODE, _key, GCMParameterSpec(TAG_LENGTH, FIXED_IV));
        return c.doFinal(encrypted);
    }

    companion object {
        private val FIXED_IV = byteArrayOf(12, 43, 127, 2, 99, 22, 6,  78,  24, 53, 8, 101);
        private const val TAG_LENGTH = 128
        private const val AES_MODE = "AES/GCM/NoPadding";
        private val TAG = "GPasswordEncryptionProviderV0";
    }
}