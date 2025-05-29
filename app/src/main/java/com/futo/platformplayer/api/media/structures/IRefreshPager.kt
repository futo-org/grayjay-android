package com.futo.platformplayer.api.media.structures

import com.futo.platformplayer.constructs.Event1

/**
 * A RefreshPager represents a pager that can be modified overtime (eg. By getting more results later, by recreating the pager)
 * When the onPagerChanged event is emitted, a new pager instance is passed, or requested via getCurrentPager
 */
interface IRefreshPager<T>: IPager<T> {
    val onPagerChanged: Event1<IPager<T>>;
    val onPagerError: Event1<Throwable>;

    fun getCurrentPager(): IPager<T>;
}