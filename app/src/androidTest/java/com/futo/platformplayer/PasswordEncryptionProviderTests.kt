package com.futo.platformplayer

import com.futo.platformplayer.encryption.EncryptionProvider
import com.futo.platformplayer.encryption.PasswordEncryptionProvider
import junit.framework.TestCase.assertEquals
import org.junit.Test

class PasswordEncryptionProviderTests {
    @Test
    fun testEncryptDecryptBytesPassword() {
        val password = "1234".padStart(32, '9');
        val encryptionProvider = PasswordEncryptionProvider(password);
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