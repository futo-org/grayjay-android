package com.futo.platformplayer.api.media.structures

import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.api.media.exceptions.search.NoNextPageException
import java.util.stream.IntStream

/**
 * A MultiPager combines several pagers of the same types, and merges them in some manner.
 * Implementations of this abstract class require to implement which item is the next one, by choosing between each provided pager
 * (eg. Implementation of MultiPager is MultiChronoContentPager which orders multiple pager contents by their datetime)
 */
abstract class MultiPager<T> : IPager<T> {
    protected val _pagerLock = Object();

    protected val _pagers : MutableList<IPager<T>>;
    protected val _subSinglePagers : MutableList<SingleItemPager<T>>;
    protected val _failedPagers: ArrayList<IPager<T>> = arrayListOf();

    private val _pageSize : Int = 9;

    private var _didInitialize = false;

    private var _currentResults : List<T> = listOf();
    private var _currentResultExceptions: Map<IPager<T>, Throwable> = mapOf();

    val allowFailure: Boolean;

    val totalPagers: Int get() = _pagers.size;

    constructor(pagers : List<IPager<T>>, allowFailure: Boolean = false) {
        this.allowFailure = allowFailure;
        _pagers = pagers.toMutableList();
        _subSinglePagers = _pagers.map { SingleItemPager(it) }.toMutableList();
    }
    fun initialize() {
        _currentResults = loadNextPage(true);
        _didInitialize = true;
    }

    override fun hasMorePages(): Boolean {
        synchronized(_pagerLock) {
            return _subSinglePagers.any { it.hasMoreItems() } || _pagers.any { it.hasMorePages() }
        }
    }
    override fun nextPage() {
        Logger.i(TAG, "Load next page");
        if(!_didInitialize)
            throw IllegalStateException("Call initialize on MultiVideoPager before using it");
        loadNextPage();
        Logger.i(TAG, "New results: ${_currentResults.size}");
    }
    override fun getResults(): List<T> {
        if(!_didInitialize)
            throw IllegalStateException("Call initialize on MultiVideoPager before using it");
        return _currentResults;
    }
    fun getResultExceptions(): Map<IPager<T>, Throwable> {
        if(!_didInitialize)
            throw IllegalStateException("Call initialize on MultiVideoPager before using it");
        return _currentResultExceptions;
    }

    @Synchronized
    private fun loadNextPage(isInitial: Boolean = false) : List<T> {
        synchronized(_pagerLock) {
            if (_subSinglePagers.size == 0)
                return listOf();
        }
        if(!isInitial && !hasMorePages())
            throw NoNextPageException();

        val results = ArrayList<T>();
        val exceptions: MutableMap<IPager<T>, Throwable> = mutableMapOf();
        for(i in IntStream.range(0, _pageSize)) {
            val validPagers = synchronized(_pagerLock) {
               _subSinglePagers.filter { !_failedPagers.contains(it.getPager()) && (it.hasMoreItems() || it.getPager().hasMorePages()) }
            };
            val options: ArrayList<SelectionOption<T>> = arrayListOf();
            for (pager in validPagers) {
                val item: T? = if (allowFailure) {
                    try {
                        pager.getCurrentItem();
                    } catch (ex: NoNextPageException) {
                        //TODO: This should never happen, has to be fixed later
                        Logger.i(TAG, "Expected item from pager but no page found?");
                        null;
                    } catch (ex: Throwable) {
                        Logger.e(TAG, "Failed to fetch page for pager, exception: ${ex.message}", ex);
                        _failedPagers.add(pager.getPager());
                        exceptions.put(pager.getPager(), ex);
                        null;
                    }
                } else {
                    try {
                        pager.getCurrentItem();
                    } catch (ex: NoNextPageException) {
                        //TODO: This should never happen, has to be fixed later
                        Logger.i(TAG, "Expected item from pager but no page found?");
                        null;
                    }
                };
                if (item != null)
                    options.add(SelectionOption(pager, item));
            }

            if (options.size == 0)
                break;
            val bestIndex = selectItemIndex(options.toTypedArray());
            if (bestIndex >= 0) {

                val consumed = options[bestIndex].pager.consumeItem();
                if (consumed != null)
                    results.add(consumed);
            }
        }
        _currentResults = results;
        _currentResultExceptions = exceptions;
        return _currentResults;
    }

    protected abstract fun selectItemIndex(options : Array<SelectionOption<T>>) : Int;

    protected class SelectionOption<T>(val pager : SingleItemPager<T>, val item : T?);

    fun setExceptions(exs: Map<IPager<T>, Throwable>) {
        _currentResultExceptions = exs;
    }
    fun findPager(query: (IPager<T>)->Boolean): IPager<*>? {
        for(pager in _pagers) {
            if(query(pager))
                return pager;
            if(pager is MultiPager<*>)
                return pager.findPager(query as (IPager<out Any?>) -> Boolean);
        }
        return null;
    }

    companion object {
        val TAG = "MultiPager";
    }
}