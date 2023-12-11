package com.futo.platformplayer.api.media.structures

/**
 * A Pager interface that implements a suspended manner of nextPage
 */
interface IAsyncPager<T> {
    fun hasMorePages() : Boolean;
    suspend fun nextPageAsync();
    fun getResults() : List<T>;
}