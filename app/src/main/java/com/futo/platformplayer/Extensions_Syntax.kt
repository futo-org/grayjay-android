package com.futo.platformplayer

import android.net.Uri
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder

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
            "[${hostAddress}]"
        }
        is Inet4Address -> {
            hostAddress
        }
        else -> {
            throw Exception("Invalid address type")
        }
    }
}