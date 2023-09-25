package com.futo.platformplayer.api.media.structures

import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.api.media.exceptions.search.NoNextPageException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.stream.IntStream
import kotlin.system.measureTimeMillis

/**
 * Async MultiPager is a multipager that calls all pagers in a deferred (promised) manner.
 * Unlike its normal counterpart which waits for results as they are needed.
 * The benefit is that if multiple pagers need to request a new page, its done in parallel and awaited together.
 * The downside is that pager results cannot be consumed based on their contents, as their contents may still be unknown.
 * (eg. example Chronological pagers cannot be done without reordering the results every refresh, which causes bad UX)
 */
abstract class MultiParallelPager<T> : IPager<T>, IAsyncPager<T> {
    protected val _pagerLock = Object();

    protected val _pagers : MutableList<IPager<T>>;
    protected val _subSinglePagers : MutableList<SingleAsyncItemPager<T>>;
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
        _subSinglePagers = _pagers.map { SingleAsyncItemPager(it) }.toMutableList();
    }
    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            _currentResults = loadNextPage(this, true);
        }
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
        runBlocking { loadNextPage(this) };
        Logger.i(TAG, "New results: ${_currentResults.size}");
    }

    override suspend fun nextPageAsync() {
        Logger.i(TAG, "Load next page (async)");
        if(!_didInitialize)
            throw IllegalStateException("Call initialize on MultiVideoPager before using it");
        withContext(Dispatchers.IO) {
            loadNextPage(this);
        }
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

    private suspend fun loadNextPage(scope: CoroutineScope, isInitial: Boolean = false) : List<T> {
        synchronized(_pagerLock) {
            if (_subSinglePagers.size == 0)
                return listOf();
        }

        if(!isInitial && !hasMorePages())
            throw NoNextPageException();

        val results = ArrayList<Deferred<T?>>();
        val exceptions: MutableMap<IPager<T>, Throwable> = mutableMapOf();
        val timeForPage = measureTimeMillis {
            for(i in IntStream.range(0, _pageSize)) {
                val validPagers = synchronized(_pagerLock) {
                    _subSinglePagers.filter { !_failedPagers.contains(it.getPager()) && (it.hasMoreItems() || it.getPager().hasMorePages()) }
                };
                val options: ArrayList<SelectionOption<T>> = arrayListOf();
                for (pager in validPagers) {
                    val item: Deferred<T?>? = if (allowFailure) {
                        try {
                            pager.getCurrentItem(scope);
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
                            pager.getCurrentItem(scope);
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

                    val consumed = options[bestIndex].pager.consumeItem(scope);
                    if (consumed != null)
                        results.add(consumed);
                }
            }
        }
        Logger.i(TAG, "Pager prepare in ${timeForPage}ms");
        val timeAwait = measureTimeMillis {
            _currentResults = results.map { it.await() }.mapNotNull { it };
        };
        Logger.i(TAG, "Pager load in ${timeAwait}ms");

        _currentResultExceptions = exceptions;
        return _currentResults;
    }

    protected abstract fun selectItemIndex(options : Array<SelectionOption<T>>) : Int;

    protected class SelectionOption<T>(val pager : SingleAsyncItemPager<T>, val item : Deferred<T?>?);

    fun setExceptions(exs: Map<IPager<T>, Throwable>) {
        _currentResultExceptions = exs;
    }
    fun findPager(query: (IPager<T>)->Boolean): IPager<*>? {
        for(pager in _pagers) {
            if(query(pager))
                return pager;
            if(pager is MultiParallelPager<*>)
                return pager.findPager(query as (IPager<out Any?>) -> Boolean);
        }
        return null;
    }

    companion object {
        val TAG = "MultiPager";
    }
}