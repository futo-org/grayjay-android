package com.futo.platformplayer

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