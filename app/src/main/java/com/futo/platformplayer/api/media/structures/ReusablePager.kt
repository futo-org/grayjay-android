package com.futo.platformplayer.api.media.structures

import com.futo.platformplayer.api.media.structures.ReusablePager.Window
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.logging.Logger

/**
 * A wrapper pager that stores previous pages of results, and provides child-pagers that read from the source.
 * This allows for a pager to be re-used in various scenarios where previous results need to be reloaded.
 * (Eg. Subscriptions feed uses it to batch requests, and respond multiple pagers with the same source without duplicate requests)
 * A "Window" is effectively a pager that just reads previous results from the shared results, but when the end is reached, it will call nextPage on the parent if possible for new results.
 * This allows multiple Windows to exist of the same pager, without messing with position, or duplicate requests
 */
open class ReusablePager<T>: INestedPager<T>, IReusablePager<T> {
    protected var _pager: IPager<T>;
    val previousResults = arrayListOf<T>();

    constructor(subPager: IPager<T>) {
        this._pager = subPager;
        synchronized(previousResults) {
            previousResults.addAll(subPager.getResults());
        }
    }

    override fun findPager(query: (IPager<T>) -> Boolean): IPager<T>? {
        if(query(_pager))
            return _pager;
        else if(_pager is INestedPager<*>)
            return (_pager as INestedPager<T>).findPager(query);
        return null;
    }

    override fun hasMorePages(): Boolean {
        return _pager.hasMorePages();
    }

    override fun nextPage() {
        _pager.nextPage();
    }

    override fun getResults(): List<T> {
        val results = _pager.getResults();
        synchronized(previousResults) {
            previousResults.addAll(results);
        }
        return previousResults;
    }

    override fun getWindow(): Window<T> {
        return Window(this);
    }


    class Window<T>: IPager<T>, INestedPager<T> {
        private val _parent: ReusablePager<T>;
        private var _position: Int = 0;
        private var _read: Int = 0;

        private var _currentResults: List<T>;


        constructor(parent: ReusablePager<T>) {
            _parent = parent;
            synchronized(_parent.previousResults) {
                _currentResults = _parent.previousResults.toList();
                _read += _currentResults.size;
            }
        }

        override fun hasMorePages(): Boolean {
            return _parent.previousResults.size > _read || _parent.hasMorePages();
        }

        override fun nextPage() {
            synchronized(_parent.previousResults) {
                if(_parent.previousResults.size <= _read) {
                    _parent.nextPage();
                    _parent.getResults();
                }
                _currentResults = _parent.previousResults.drop(_read).toList();
                _read += _currentResults.size;
            }
        }

        override fun getResults(): List<T> {
            return _currentResults;
        }

        override fun findPager(query: (IPager<T>) -> Boolean): IPager<T>? {
            return _parent.findPager(query);
        }

    }

    companion object {
        fun <T> IPager<T>.asReusable(): ReusablePager<T> {
            return ReusablePager(this);
        }
    }
}


public class ReusableRefreshPager<T>: INestedPager<T>, IReusablePager<T> {
    protected var _pager: IRefreshPager<T>;
    val previousResults = arrayListOf<T>();

    private var _currentPage: IPager<T>;


    val onPagerChanged = Event1<IPager<T>>()
    val onPagerError = Event1<Throwable>()

    constructor(subPager: IRefreshPager<T>) {
        this._pager = subPager;
        _currentPage = this;
        synchronized(previousResults) {
            previousResults.addAll(subPager.getResults());
        }
        _pager.onPagerError.subscribe(onPagerError::emit);
        _pager.onPagerChanged.subscribe {
            _currentPage = it;
            synchronized(previousResults) {
                previousResults.clear();
                previousResults.addAll(it.getResults());
            }

            onPagerChanged.emit(_currentPage);
        };
    }

    override fun findPager(query: (IPager<T>) -> Boolean): IPager<T>? {
        if(query(_pager))
            return _pager;
        else if(_pager is INestedPager<*>)
            return (_pager as INestedPager<T>).findPager(query);
        return null;
    }

    override fun hasMorePages(): Boolean {
        return _pager.hasMorePages();
    }

    override fun nextPage() {
        _pager.nextPage();
    }

    override fun getResults(): List<T> {
        val results = _pager.getResults();
        synchronized(previousResults) {
            previousResults.addAll(results);
        }
        return previousResults;
    }

    override fun getWindow(): RefreshWindow<T> {
        return RefreshWindow(this);
    }


    class RefreshWindow<T>: IPager<T>, INestedPager<T>, IRefreshPager<T> {
        private val _parent: ReusableRefreshPager<T>;
        private var _position: Int = 0;
        private var _read: Int = 0;

        private var _currentResults: List<T>;

        override val onPagerChanged = Event1<IPager<T>>();
        override val onPagerError = Event1<Throwable>();


        override fun getCurrentPager(): IPager<T> {
            return _parent.getWindow();
        }

        constructor(parent: ReusableRefreshPager<T>) {
            _parent = parent;

            synchronized(_parent.previousResults) {
                _currentResults = _parent.previousResults.toList();
                _read += _currentResults.size;
            }
            parent.onPagerChanged.subscribe(onPagerChanged::emit);
            parent.onPagerError.subscribe(onPagerError::emit);
        }


        override fun hasMorePages(): Boolean {
            return _parent.previousResults.size > _read || _parent.hasMorePages();
        }

        override fun nextPage() {
            synchronized(_parent.previousResults) {
                if (_parent.previousResults.size <= _read) {
                    _parent.nextPage();
                    _parent.getResults();
                }
                _currentResults = _parent.previousResults.drop(_read).toList();
                _read += _currentResults.size;
            }
        }

        override fun getResults(): List<T> {
            return _currentResults;
        }

        override fun findPager(query: (IPager<T>) -> Boolean): IPager<T>? {
            return _parent.findPager(query);
        }
    }
}

interface IReusablePager<T>: IPager<T> {
    fun getWindow(): IPager<T>;
}