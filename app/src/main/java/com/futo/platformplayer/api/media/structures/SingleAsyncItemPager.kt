package com.futo.platformplayer.api.media.structures

import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

/**
 * SingleItemPagers are used to wrap any IPager<T> and consume items 1 at a time.
 * Often used by MultiPagers to add items to a page one at a time from multiple pagers
 * Unlike its non-async counterpart, It returns a Deferred item which is either constant, or a future page response if a nextPage has to be called on the base pager.
 */
class SingleAsyncItemPager<T> {
    private val _pager : IPager<T>;
    private var _currentResultPos : Int;
    private var _currentPagerStartPos: Int = 0;
    private var _currentPagerEndPos: Int = 0;

    private var _requestedPageItems: ArrayList<CompletableDeferred<T?>?> = arrayListOf();

    private var _isRequesting = false;

    constructor(pager: IPager<T>) {
        _pager = pager;
        val results = _pager.getResults()
        for(result in results)
            _requestedPageItems.add(CompletableDeferred(result));
        _currentResultPos = 0;
        _currentPagerEndPos = results.size;
    }

    fun getPager() : IPager<T> = _pager;

    fun hasMoreItems() : Boolean = _currentResultPos < _currentPagerEndPos;

    @Synchronized
    fun getCurrentItem(scope: CoroutineScope) : Deferred<T?>? {
        synchronized(_requestedPageItems) {
            if (_currentResultPos >= _requestedPageItems.size) {
                val startPos = fillDeferredUntil(_currentResultPos);
                if(!_pager.hasMorePages()) {
                    Logger.i("SingleAsyncItemPager", "end of async page reached");
                    completeRemainder { it?.complete(null) };
                }
                if(_isRequesting)
                    return _requestedPageItems[_currentResultPos];
                _isRequesting = true;
                StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
                    try {
                        Logger.i("SingleAsyncItemPager", "Started Pager");
                        val timeForPage = measureTimeMillis { _pager.nextPage() };
                        val newResults = _pager.getResults();
                        Logger.i("SingleAsyncItemPager", "Finished Pager (${timeForPage}ms)");
                        _currentPagerStartPos = _currentPagerEndPos;
                        _currentPagerEndPos = _currentPagerStartPos + newResults.size;
                        synchronized(_requestedPageItems) {
                            fillDeferredUntil(_currentPagerEndPos)
                            for (i in newResults.indices)
                                _requestedPageItems[_currentPagerStartPos + i]!!.complete(newResults[i]);
                            completeRemainder {
                                it?.complete(null);
                            };
                        }
                    }
                    catch(ex: Throwable) {
                        Logger.e("SingleAsyncItemPager", "Pager exception", ex);
                        synchronized(_requestedPageItems) {
                            fillDeferredUntil(_currentPagerEndPos);

                            completeRemainder {
                                it?.completeExceptionally(ex);
                            };
                        }
                    }
                    finally {
                        synchronized(_requestedPageItems) {
                            _isRequesting = false;
                        }
                    }
                }

                return _requestedPageItems[_currentResultPos];
            }
            if (_requestedPageItems.size > _currentResultPos)
                return _requestedPageItems[_currentResultPos];
            else return null;
        }
    }

    @Synchronized
    fun consumeItem(scope: CoroutineScope) : Deferred<T?>? {
        val result = getCurrentItem(scope);
        _currentResultPos++;
        return result;
    }

    private fun fillDeferredUntil(i: Int): Int {
        val startPos = _requestedPageItems.size;
        for(i in _requestedPageItems.size..i) {
            _requestedPageItems.add(CompletableDeferred());
        }
        return startPos;
    }
    private fun completeRemainder(completer: (CompletableDeferred<T?>?)->Unit) {
        synchronized(_requestedPageItems) {
            for(i in _currentPagerEndPos until _requestedPageItems.size)
                completer(_requestedPageItems[i]);
        }
    }
}