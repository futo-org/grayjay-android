package com.futo.platformplayer.api.media.structures

import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.constructs.Event1

class RefreshDedupContentPager: IRefreshPager<IPlatformContent>, IPager<IPlatformContent> {
    private val _basePager: MultiRefreshPager<IPlatformContent>;
    private var _currentPage: IPager<IPlatformContent>;

    override val onPagerChanged = Event1<IPager<IPlatformContent>>();
    override val onPagerError = Event1<Throwable>();


    constructor(refreshPager: MultiRefreshPager<IPlatformContent>, preferredPlatform: List<String>? = null) : super() {
        _basePager = refreshPager;
        _currentPage = DedupContentPager(_basePager.getCurrentPager(), preferredPlatform);
        _basePager.onPagerError.subscribe(onPagerError::emit);
        _basePager.onPagerChanged.subscribe { 
            _currentPage = DedupContentPager(it, preferredPlatform);
            onPagerChanged.emit(_currentPage);
        };
    }

    override fun getCurrentPager(): IPager<IPlatformContent> = _currentPage;
    override fun hasMorePages(): Boolean {
        return _basePager.hasMorePages();
    }

    override fun nextPage() {
        return _basePager.nextPage();
    }

    override fun getResults(): List<IPlatformContent> {
        return _basePager.getResults();
    }
}