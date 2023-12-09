package com.futo.platformplayer.api.media.structures

import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.assume
import kotlin.streams.asSequence
import kotlin.streams.toList

/**
 * Old wrapper for Platform Content
 * Marked for deletion (?)
 */
class PlatformContentPager : IPager<IPlatformContent> {
    private val _items : List<IPlatformContent>;
    private var _page = 0;
    private val _pageSize : Int;
    private var _currentItems : List<IPlatformContent>;

    constructor(items : List<IPlatformContent>, itemsPerPage : Int = 20) {
        _items = items;
        _pageSize = itemsPerPage;
        _currentItems = items.take(itemsPerPage).toList();
    }

    override fun hasMorePages(): Boolean {
        return _items.size > (_page + 1) * _pageSize;
    }

    override fun nextPage() {
        _page++;
        _currentItems = _items.stream()
            .skip((_page * _pageSize).toLong())
            .asSequence()
            .toList()
            .take(_pageSize)
            .toList();
    }

    override fun getResults(): List<IPlatformContent> {
        return _currentItems;
    }
}