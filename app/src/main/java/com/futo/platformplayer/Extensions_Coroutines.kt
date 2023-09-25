package com.futo.platformplayer

import android.util.Log
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.selects.select
import java.lang.IllegalArgumentException

//Syntax sugaring
suspend inline fun <reified T> Collection<Deferred<T>>.awaitFirst(): T?{
    val tasks = this;
    if (tasks.isEmpty()) {
        return null;
    }

    var result: T? = null;
    select<Boolean> {
        tasks.forEach { def ->
            def.onAwait {
                result = it;
                true;
            }
        }
    }
    return result;
}
suspend inline fun <reified T> Collection<Deferred<T>>.awaitFirstDeferred(): Pair<Deferred<T>, T> {
    if (isEmpty()) {
        throw IllegalArgumentException("Cannot be called on empty list");
    }

    return select {
        this@awaitFirstDeferred.onEach { deferred ->
            deferred.onAwait { result ->
                Pair(deferred, result)
            }
        }
    }
}

suspend inline fun <reified T> Collection<Deferred<T?>>.awaitFirstNotNullDeferred(): Pair<Deferred<T?>, T>? {
    if (isEmpty()) {
        return null;
    }

    val toAwait = this.toMutableList();
    while(toAwait.isNotEmpty()) {
        val result = select {
            toAwait.onEach { deferred ->
                deferred.onAwait { result ->
                    Pair(deferred, result)
                }
            }
        }

        if(result.second != null) {
            return Pair(result.first, result.second!!);
        }

        toAwait.remove(result.first);
    }
    return null;
}