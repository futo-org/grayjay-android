package com.futo.platformplayer.polycentric

import com.futo.platformplayer.polycentric.Key as LegacyKey
import com.google.protobuf.ByteString
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/**
 * Tests for the migration from v1 to v2 signing keys.
 */
class PolycentricKeyStorageTests {
    private fun randomSeed(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    private fun secretFor(seed: ByteArray): StorageTypeProcessSecret =
        StorageTypeProcessSecret.newBuilder()
            .setSystem(LegacyKey.newBuilder()
                .setKeyType(PolycentricKeyStorage.KEY_TYPE_ED25519)
                .setKey(ByteString.copyFrom(seed)))
            .setProcess(Process.newBuilder().setProcess(ByteString.copyFrom(ByteArray(16))))
            .build()

    @Test
    fun `derived public key matches BouncyCastle keypair`() {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()
        val seed = (keyPair.private as Ed25519PrivateKeyParameters).encoded
        val publicKey = (keyPair.public as Ed25519PublicKeyParameters).encoded

        assertTrue(seed.size == 32)
        val derived = PolycentricKeyStorage.deriveEd25519PublicKey(seed)
        assertTrue(derived.contentEquals(publicKey))
    }

    @Test
    fun `secret matches its derived public key`() {
        val seed = randomSeed()
        val secret = secretFor(seed)
        val publicKey = PolycentricKeyStorage.deriveEd25519PublicKey(seed)

        assertTrue(PolycentricKeyStorage.secretMatchesPublicKey(secret, publicKey))
    }

    @Test
    fun `secret does not match a foreign or mismatched key`() {
        val seed = randomSeed()
        val secret = secretFor(seed)

        assertFalse(PolycentricKeyStorage.secretMatchesPublicKey(secret, seed))
        assertFalse(PolycentricKeyStorage.secretMatchesPublicKey(secret, randomSeed()))
    }

    @Test
    fun `malformed secrets never match`() {
        val seed = randomSeed()

        val wrongType = StorageTypeProcessSecret.newBuilder()
            .setSystem(LegacyKey.newBuilder()
                .setKeyType(2L)
                .setKey(ByteString.copyFrom(seed)))
            .build()
        assertFalse(PolycentricKeyStorage.secretMatchesPublicKey(wrongType, PolycentricKeyStorage.deriveEd25519PublicKey(seed)))

        val shortKey = StorageTypeProcessSecret.newBuilder()
            .setSystem(LegacyKey.newBuilder()
                .setKeyType(PolycentricKeyStorage.KEY_TYPE_ED25519)
                .setKey(ByteString.copyFrom(ByteArray(16))))
            .build()
        assertFalse(PolycentricKeyStorage.secretMatchesPublicKey(shortKey, ByteArray(32)))

        assertEquals(32, PolycentricKeyStorage.deriveEd25519PublicKey(seed).size)
    }
}
