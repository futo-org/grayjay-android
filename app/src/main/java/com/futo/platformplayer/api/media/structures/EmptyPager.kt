package com.futo.platformplayer.api.media.structures

/**
 * A pager without results
 */
open class EmptyPager<TResult> : IPager<TResult> {
    override fun hasMorePages(): Boolean {
        return false;
    }

    override fun nextPage() {

    }

    override fun getResults(): List<TResult> {
        return listOf();
    }

}