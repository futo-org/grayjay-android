package com.futo.platformplayer.polycentric

import com.google.protobuf.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPOutputStream

/**
 * Tests for parsing Polycentric v1 profile exports.
 */
class LegacyExportParseTests {
    private fun randomSeed(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    private fun keyPair(seed: ByteArray, keyType: Long = PolycentricKeyStorage.KEY_TYPE_ED25519,
                        publicKey: ByteArray = PolycentricKeyStorage.deriveEd25519PublicKey(seed)) =
        LegacyKeyPair.newBuilder()
            .setKeyType(keyType)
            .setPrivateKey(ByteString.copyFrom(seed))
            .setPublicKey(ByteString.copyFrom(publicKey))
            .build()

    private fun urlInfo(keyPair: LegacyKeyPair, urlType: Long = 3L): ByteArray =
        LegacyURLInfo.newBuilder()
            .setUrlType(urlType)
            .setBody(LegacyExportBundle.newBuilder().setKeyPair(keyPair).build().toByteString())
            .build()
            .toByteArray()

    /** URL-safe and unpadded base64 conversion, matching v1's `toBase64Url` */
    private fun export(data: ByteArray): String =
        "polycentric://" + Base64.getUrlEncoder().withoutPadding().encodeToString(data)

    private fun gzip(data: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(data) } }.toByteArray()

    @Test
    fun `parses export`() {
        val seed = randomSeed()
        assertArrayEquals(seed, parseLegacyExport(export(urlInfo(keyPair(seed)))))
    }

    @Test
    fun `parses gzipped export with surrounding whitespace`() {
        val seed = randomSeed()
        assertArrayEquals(seed, parseLegacyExport("\n " + export(gzip(urlInfo(keyPair(seed)))) + "\n"))
    }

    @Test
    fun `parses export without public key`() {
        val seed = randomSeed()
        assertArrayEquals(seed, parseLegacyExport(export(urlInfo(keyPair(seed, publicKey = ByteArray(0))))))
    }

    @Test
    fun `rejects missing prefix`() {
        val data = Base64.getUrlEncoder().encodeToString(urlInfo(keyPair(randomSeed())))
        assertThrows(LegacyExportParseException::class.java) { parseLegacyExport(data) }
    }

    @Test
    fun `rejects non export url type`() {
        assertThrows(LegacyExportParseException::class.java) {
            parseLegacyExport(export(urlInfo(keyPair(randomSeed()), urlType = 1L)))
        }
    }

    @Test
    fun `rejects unsupported key type`() {
        assertThrows(LegacyExportParseException::class.java) {
            parseLegacyExport(export(urlInfo(keyPair(randomSeed(), keyType = 2L))))
        }
    }

    @Test
    fun `rejects wrong seed length`() {
        val seed = randomSeed()
        val pair = keyPair(seed).toBuilder().setPrivateKey(ByteString.copyFrom(seed + seed)).build()
        assertThrows(LegacyExportParseException::class.java) { parseLegacyExport(export(urlInfo(pair))) }
    }

    @Test
    fun `rejects mismatched public key`() {
        val pair = keyPair(randomSeed(), publicKey = PolycentricKeyStorage.deriveEd25519PublicKey(randomSeed()))
        assertThrows(LegacyExportParseException::class.java) { parseLegacyExport(export(urlInfo(pair))) }
    }

    @Test
    fun `rejects garbage`() {
        assertThrows(LegacyExportParseException::class.java) { parseLegacyExport("polycentric://!!!") }
        assertThrows(LegacyExportParseException::class.java) {
            parseLegacyExport(export(byteArrayOf(0x0a, 0x7f, 0x01)))
        }
        assertThrows(LegacyExportParseException::class.java) {
            parseLegacyExport(export(byteArrayOf(0x1f, 0x8b.toByte(), 0x00)))
        }
    }
}
