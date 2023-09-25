package com.futo.platformplayer.api.media.structures

/**
 * Interface extension for some pagers that allow you to find nested pagers if needed
 */
interface INestedPager<T> {
    fun findPager(query: (IPager<T>) -> Boolean): IPager<T>?;
}