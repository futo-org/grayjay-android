package com.futo.platformplayer.polycentric

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import polycentric.v2.PairingInfo
import polycentric.v2.PublicKey
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64

/** Number of emojis in a pairing fingerprint. */
const val FINGERPRINT_LENGTH = 3

/** Formats [key] as `"<keyType>_<key in lowercase hex>"`. */
fun publicKeyToString(key: PublicKey): String = "${key.key_type.value}_${key.key.hex()}"

/**
 * The emojis both devices show as a visualization of the new device's [key] while pairing. The user
 * checks that they match.
 */
fun emojiFingerprint(key: PublicKey): List<String> = emojiFingerprintOfString(publicKeyToString(key))

/**
 * Picks [FINGERPRINT_LENGTH] emojis from [HARBOR_FINGERPRINT_POOL] based on the SHA-256 hash of
 * [text]. Each emoji uses the next 4 bytes of the hash as an unsigned number, wrapped to the pool
 * size. Mirrors Harbor's `publicKeyEmojiFingerprint`.
 */
fun emojiFingerprintOfString(text: String): List<String> {
    val hash = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
    val pool = HARBOR_FINGERPRINT_POOL
    return List(FINGERPRINT_LENGTH) { i ->
        val index = (hash.getInt(i * 4).toLong() and 0xFFFFFFFFL) % pool.size
        pool[index.toInt()]
    }
}

/** Path of Harbor's pairing link. */
const val PAIRING_LINK_PATH = "/login/pair"

/** Query parameter that holds the pairing code. */
const val PAIRING_CODE_PARAM = "code"

/**
 * Builds a link for another device to open:
 * `<baseUrl>/<PAIRING_LINK_PATH>?<PAIRING_CODE_PARAM>=<code>`. The code is [info] using URL-safe
 * base64 without padding.
 */
fun encodePairingLink(info: PairingInfo, baseUrl: String = PolycentricAdapter.WEB_BASE_URL): String {
    val code = Base64.getUrlEncoder().withoutPadding().encodeToString(PairingInfo.ADAPTER.encode(info))
    return baseUrl.toHttpUrl().newBuilder()
        .addPathSegments(PAIRING_LINK_PATH.removePrefix("/"))
        .addQueryParameter(PAIRING_CODE_PARAM, code)
        .build()
        .toString()
}

/**
 * Decodes [PairingInfo] from a pairing link or a code. Returns null if [input] is neither. A code
 * can be hex or base64. Mirrors Harbor's `extractPairingInfo`.
 */
fun parsePairingInfo(input: String): PairingInfo? {
    val trimmed = input.trim()
    var code = trimmed

    val link = pairingLinkUrl(trimmed)
    if (link != null) {
        // A link without a code is invalid.
        code = link.queryParameter(PAIRING_CODE_PARAM) ?: return null
    }

    return decodePairingCode(decodeHex(code)) ?: decodePairingCode(decodeBase64(code))
}

/** Whether [url] looks like a pairing link. */
fun isPairingLink(url: String): Boolean = pairingLinkUrl(url.trim()) != null

private fun pairingLinkUrl(text: String): HttpUrl? =
    text.toHttpUrlOrNull()?.takeIf { it.encodedPath.trimEnd('/') == PAIRING_LINK_PATH }

private fun decodePairingCode(bytes: ByteArray?): PairingInfo? {
    if (bytes == null || bytes.isEmpty()) return null
    val info = try {
        PairingInfo.ADAPTER.decode(bytes)
    } catch (_: Exception) {
        return null
    }
    if (info.server.isEmpty() || info.digest_sha256.size == 0) return null
    return info
}

private val HEX_REGEX = Regex("^[0-9a-fA-F]*$")
private val BASE64_REGEX = Regex("^[A-Za-z0-9+/_-]*={0,2}$")

private fun decodeHex(hex: String): ByteArray? {
    if (hex.length % 2 != 0 || !HEX_REGEX.matches(hex)) return null
    return ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

private fun decodeBase64(text: String): ByteArray? {
    if (!BASE64_REGEX.matches(text)) return null
    // Convert URL-safe base64 to standard base64
    val standard = text.replace('-', '+').replace('_', '/')
    return try {
        Base64.getDecoder().decode(standard)
    } catch (_: IllegalArgumentException) {
        null
    }
}
