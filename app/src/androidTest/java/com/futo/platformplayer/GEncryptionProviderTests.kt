package com.futo.platformplayer

import com.futo.platformplayer.encryption.GEncryptionProviderV0
import com.futo.platformplayer.encryption.GEncryptionProviderV1
import junit.framework.TestCase.assertEquals
import org.junit.Test

class GEncryptionProviderTests {
    @Test
    fun testEncryptDecryptV1() {
        val encryptionProvider = GEncryptionProviderV1.instance
        val plaintext = "This is a test string."

        // Encrypt the plaintext
        val ciphertext = encryptionProvider.encrypt(plaintext)

        // Decrypt the ciphertext
        val decrypted = encryptionProvider.decrypt(ciphertext)

        // The decrypted string should be equal to the original plaintext
        assertEquals(plaintext, decrypted)
    }


    @Test
    fun testEncryptDecryptBytesV1() {
        val encryptionProvider = GEncryptionProviderV1.instance
        val bytes = "This is a test string.".toByteArray();

        // Encrypt the plaintext
        val ciphertext = encryptionProvider.encrypt(bytes)

        // Decrypt the ciphertext
        val decrypted = encryptionProvider.decrypt(ciphertext)

        // The decrypted string should be equal to the original plaintext
        assertArrayEquals(bytes, decrypted);
    }


    @Test
    fun testEncryptDecryptV0() {
        val encryptionProvider = GEncryptionProviderV0.instance
        val plaintext = "This is a test string."

        // Encrypt the plaintext
        val ciphertext = encryptionProvider.encrypt(plaintext)

        // Decrypt the ciphertext
        val decrypted = encryptionProvider.decrypt(ciphertext)

        // The decrypted string should be equal to the original plaintext
        assertEquals(plaintext, decrypted)
    }


    @Test
    fun testEncryptDecryptBytesV0() {
        val encryptionProvider = GEncryptionProviderV0.instance
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