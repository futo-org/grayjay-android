package com.futo.platformplayer.api.media.structures

import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import kotlinx.coroutines.Deferred

/**
 * SingleItemPagers are used to wrap any IPager<T> and consume items 1 at a time.
 * Often used by MultiPagers to add items to a page one at a time from multiple pagers
 */
class SingleItemPager<T> {
    private val _pager : IPager<T>;
    private var _currentResult : List<T>;
    private var _currentResultPos : Int;




    constructor(pager: IPager<T>) {
        _pager = pager;
        _currentResult = _pager.getResults();
        _currentResultPos = 0;
    }

    fun getPager() : IPager<T> = _pager;

    fun hasMoreItems() : Boolean = _currentResultPos < _currentResult.size;

    @Synchronized
    fun getCurrentItem() : T? {
        if(_currentResultPos >= _currentResult.size) {
            _pager.nextPage();
            _currentResult = _pager.getResults();
            _currentResultPos = 0;
        }
        if(_currentResult.size > _currentResultPos)
            return _currentResult[_currentResultPos];
        else return null;
    }

    @Synchronized
    fun consumeItem() : T? {
        val result = getCurrentItem();
        _currentResultPos++;
        return result;
    }
}