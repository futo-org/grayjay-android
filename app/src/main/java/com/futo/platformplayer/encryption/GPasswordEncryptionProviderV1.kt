package com.futo.platformplayer.encryption

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class GPasswordEncryptionProviderV1 {
    fun encrypt(decrypted: String, password: String): String {
        val encrypted = encrypt(decrypted.toByteArray(), password);
        val encoded = Base64.encodeToString(encrypted, Base64.DEFAULT);
        return encoded;
    }

    fun encrypt(decrypted: ByteArray, password: String): ByteArray {
        val saltBytes = generateSalt()
        val ivBytes = generateIv()
        val c: Cipher = Cipher.getInstance(AES_MODE);
        val key = deriveKeyFromPassword(password, saltBytes)

        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, ivBytes));
        val encodedBytes: ByteArray = c.doFinal(decrypted);
        return saltBytes + ivBytes + encodedBytes;
    }

    fun decrypt(data: String, password: String): String {
        val bytes = Base64.decode(data, Base64.DEFAULT)
        return String(decrypt(bytes, password));
    }
    fun decrypt(bytes: ByteArray, password: String): ByteArray {
        val encrypted = bytes.sliceArray(IntRange(SALT_SIZE + IV_SIZE, bytes.size - 1))
        val ivBytes = bytes.sliceArray(IntRange(SALT_SIZE, SALT_SIZE + IV_SIZE - 1))
        val saltBytes = bytes.sliceArray(IntRange(0, SALT_SIZE - 1))
        val key = deriveKeyFromPassword(password, saltBytes)

        val c = Cipher.getInstance(AES_MODE);
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, ivBytes));
        return c.doFinal(encrypted);
    }

    private fun deriveKeyFromPassword(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    private fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE)
        random.nextBytes(salt)
        return salt
    }

    private fun generateIv(): ByteArray {
        val r = SecureRandom()
        val ivBytes = ByteArray(IV_SIZE)
        r.nextBytes(ivBytes)
        return ivBytes
    }

    companion object {
        val instance = GPasswordEncryptionProviderV1();
        private const val AES_MODE = "AES/GCM/NoPadding";
        private const val IV_SIZE = 12
        private const val SALT_SIZE = 16
        private const val ITERATION_COUNT = 2 * 65536
        private const val KEY_LENGTH = 256
        private const val TAG_LENGTH = 128
        private val TAG = "GPasswordEncryptionProviderV1";
    }
}