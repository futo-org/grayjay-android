package com.futo.platformplayer.api.media.structures

/**
 * Base pager used for all paging in the app, often wrapped by various other pagers to modified behavior
 */
interface IPager<T> {
    fun hasMorePages() : Boolean;
    fun nextPage();
    fun getResults() : List<T>;
}