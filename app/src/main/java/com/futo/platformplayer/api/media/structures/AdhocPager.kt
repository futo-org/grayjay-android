package com.futo.platformplayer.api.media.structures

class AdhocPager<T>: IPager<T> {
    private var _page = 0;
    private val _nextPage: (Int) -> List<T>;
    private var _currentResults: List<T> = listOf();
    private var _hasMore = true;

    constructor(nextPage: (Int) -> List<T>, initialResults: List<T>? = null){
        _nextPage = nextPage;
        if(initialResults != null)
            _currentResults = initialResults;
        else
            nextPage();
    }

    override fun hasMorePages(): Boolean {
        return _hasMore;
    }

    override fun nextPage() {
        val newResults = _nextPage(++_page);
        if(newResults.isEmpty())
            _hasMore = false;
        _currentResults = newResults;
    }

    override fun getResults(): List<T> {
        return _currentResults;
    }
}