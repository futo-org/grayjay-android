package com.futo.platformplayer

import android.net.Uri
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

fun String?.toURIRobust(): URI? {
    if (this == null) {
        return null
    }

    try {
        return URI(this)
    } catch (e: URISyntaxException) {
        val parts = this.split("\\?".toRegex(), 2)
        if (parts.size < 2) {
            return null
        }

        val beforeQuery = parts[0]
        val query = parts[1]
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val rebuiltUrl = "$beforeQuery?$encodedQuery"
        return URI(rebuiltUrl)
    }
}