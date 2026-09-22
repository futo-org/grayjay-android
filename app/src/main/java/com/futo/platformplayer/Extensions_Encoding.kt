package com.futo.platformplayer

import android.util.Base64

fun ByteArray.toBase64(): String {
    return Base64.encodeToString(this, Base64.NO_PADDING or Base64.NO_WRAP)
}
fun ByteArray.toBase64Url(): String {
    return Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
fun String.base64UrlToByteArray(): ByteArray {
    return Base64.decode(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

fun String.base64ToByteArray(): ByteArray {
    return Base64.decode(this, Base64.NO_PADDING or Base64.NO_WRAP)
}

fun String.hexStringToByteArray(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }

    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

/**
 * Folds already-computed field hash codes into one composite hash (Java `31 * result + h`
 * convention, nulls as 0).
 */
fun combineHashCodes(hashCodes: List<Int?>): Int {
    var result = 1
    for (hashCode in hashCodes) {
        result = 31 * result + (hashCode ?: 0)
    }
    return result
}
