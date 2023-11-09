package com.futo.platformplayer

import com.futo.platformplayer.encryption.GPasswordEncryptionProviderV0
import com.futo.platformplayer.encryption.GPasswordEncryptionProviderV1
import junit.framework.TestCase.assertEquals
import org.junit.Test

class GPasswordEncryptionProviderTests {
    @Test
    fun testEncryptDecryptBytesPasswordV1() {
        val encryptionProvider = GPasswordEncryptionProviderV1();
        val bytes = "This is a test string.".toByteArray();

        // Encrypt the plaintext
        val ciphertext = encryptionProvider.encrypt(bytes, "1234")

        // Decrypt the ciphertext
        val decrypted = encryptionProvider.decrypt(ciphertext, "1234")

        // The decrypted string should be equal to the original plaintext
        assertArrayEquals(bytes, decrypted);
    }

    @Test
    fun testEncryptDecryptBytesPasswordV0() {
        val encryptionProvider = GPasswordEncryptionProviderV0("1234".padStart(32, '9'));
        val bytes = "This is a test string.".toByteArray();

        // Encrypt the plaintext
        val ciphertext = encryptionProvider.encrypt(bytes)

        // Decrypt the ciphertext
        val decrypted = encryptionProvider.decrypt(ciphertext)

        // The decrypted string should be equal to the original plaintext
        assertArrayEquals(bytes, decrypted);
    }

    private fun assertArrayEquals(a: ByteArray, b: ByteArray) {
        assertEquals(a.size, b.size);
        for(i in 0 until a.size) {
            assertEquals(a[i], b[i]);
        }
    }
}