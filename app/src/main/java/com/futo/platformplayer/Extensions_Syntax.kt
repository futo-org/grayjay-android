package com.futo.platformplayer

import android.net.Uri
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

//Syntax sugaring
inline fun <reified T> Any.assume(): T?{
    if(this is T)
        return this;
    else
        return null;
}
inline fun <reified T, R> Any.assume(cb: (T) -> R): R? {
    val result = this.assume<T>();
    if(result != null)
        return cb(result);
    return null;
}

fun String?.yesNoToBoolean(): Boolean {
    return this?.uppercase() == "YES"
}

fun Boolean?.toYesNo(): String {
    return if (this == true) "YES" else "NO"
}

fun InetAddress?.toUrlAddress(): String {
    return when (this) {
        is Inet6Address -> {
            val hostAddr = this.hostAddress ?: throw Exception("Invalid address: hostAddress is null")
            val index = hostAddr.indexOf('%')
            if (index != -1) {
                val addrPart = hostAddr.substring(0, index)
                val scopeId = hostAddr.substring(index + 1)
                "[${addrPart}%25${scopeId}]" // %25 is URL-encoded '%'
            } else {
                "[$hostAddr]"
            }
        }
        is Inet4Address -> {
            this.hostAddress ?: throw Exception("Invalid address: hostAddress is null")
        }
        else -> {
            throw Exception("Invalid address type")
        }
    }
}

fun Long?.sToOffsetDateTimeUTC(): OffsetDateTime {
    if (this == null || this < 0)
        return OffsetDateTime.MIN
    if(this > 4070912400)
        return OffsetDateTime.MAX;
    return OffsetDateTime.ofInstant(Instant.ofEpochSecond(this), ZoneOffset.UTC)
}

fun Long?.msToOffsetDateTimeUTC(): OffsetDateTime {
    if (this == null || this < 0)
        return OffsetDateTime.MIN
    if(this > 4070912400)
        return OffsetDateTime.MAX;
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneOffset.UTC)
}