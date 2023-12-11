package com.futo.platformplayer.api.media.structures

import com.futo.platformplayer.api.media.models.contents.PlatformContentPlaceholder
import com.futo.platformplayer.api.media.structures.ReusablePager.Companion.asReusable
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.logging.Logger
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking

/**
 * Refresh pager is a managed multiple pagers of which some are promised/deferred, and optionally inserts placeholdesr for not yet finished promised pagers.
 * RefreshMultiPager has no inherit logic on how the pagers are read, and solely manages awaiting pagers, and "refreshing" them when new ones become available.
 * The abstract recreatePager method is intended to implement the exact pager used that is given to then consumer.
 * (Eg. RefreshDistributionContentPager returns the pagers as a equal distribution from each pager)
 */
abstract class MultiRefreshPager<T>: IRefreshPager<T>, IPager<T> {
    override val onPagerChanged: Event1<IPager<T>> = Event1();
    override val onPagerError: Event1<Throwable> = Event1();

    private val _pagersReusable: MutableList<ReusablePager<T>>;
    private var _currentPager: IPager<T>;
    private val _addPlaceholders = false;
    private val _totalPagers: Int;
    private val _placeHolderPagersPaired: Map<Deferred<IPager<T>?>, IPager<T>>;

    private val _pending: MutableList<Deferred<IPager<T>?>>;

    @OptIn(ExperimentalCoroutinesApi::class)
    constructor(pagers: List<IPager<T>>, pendingPagers: List<Deferred<IPager<T>?>>, placeholderPagers: List<IPager<T>>? = null) {
        _pagersReusable = pagers.map { ReusablePager(it) }.toMutableList();
        _totalPagers = pagers.size + pendingPagers.size;
        _placeHolderPagersPaired = placeholderPagers?.take(pendingPagers.size)?.mapIndexed { i, pager ->
            return@mapIndexed Pair(pendingPagers[i], pager);
        }?.toMap() ?: mapOf();
        _pending = pendingPagers.toMutableList();

        for(pendingPager in pendingPagers)
            pendingPager.invokeOnCompletion { error ->
                synchronized(_pending) {
                    _pending.remove(pendingPager);
                }
                if(error != null) {
                    onPagerError.emit(error);
                    val replacing = _placeHolderPagersPaired[pendingPager];
                    if(replacing != null)
                        updatePager(null, replacing, error);
                }
                else
                    updatePager(pendingPager.getCompleted());
            }
        synchronized(_pagersReusable) {
            _currentPager = recreatePager(getCurrentSubPagers());

            if(_currentPager is MultiParallelPager<*>)
                runBlocking { (_currentPager as MultiParallelPager).initialize(); };
            else if(_currentPager is MultiPager<*>)
                (_currentPager as MultiPager).initialize();

            onPagerChanged.emit(_currentPager);
        }
    }

    abstract fun recreatePager(pagers: List<IPager<T>>): IPager<T>;

    override fun hasMorePages(): Boolean = synchronized(_pagersReusable){ _currentPager.hasMorePages() };
    override fun nextPage() = synchronized(_pagersReusable){ _currentPager.nextPage() };
    override fun getResults(): List<T> = synchronized(_pagersReusable){ _currentPager.getResults() };

    private fun updatePager(pagerToAdd: IPager<T>?, toReplacePager: IPager<T>? = null, error: Throwable? = null) {
        synchronized(_pagersReusable) {
            if(pagerToAdd == null) {
                if(toReplacePager != null && toReplacePager is PlaceholderPager && error != null) {
                    val pluginId = toReplacePager.placeholderFactory.invoke().id?.pluginId ?: "";

                    _pagersReusable.add((PlaceholderPager(5) {
                        return@PlaceholderPager PlatformContentPlaceholder(pluginId, error)
                    } as IPager<T>).asReusable());
                    _currentPager = recreatePager(getCurrentSubPagers());

                    if(_currentPager is MultiParallelPager<*>)
                        runBlocking { (_currentPager as MultiParallelPager).initialize(); };
                    else if(_currentPager is MultiPager<*>)
                        (_currentPager as MultiPager).initialize()

                    onPagerChanged.emit(_currentPager);
                }
                return;
            }
            Logger.i("RefreshMultiDistributionContentPager", "Received new pager for RefreshPager")
            _pagersReusable.add(pagerToAdd.asReusable());

            _currentPager = recreatePager(getCurrentSubPagers());

            if(_currentPager is MultiParallelPager<*>)
                runBlocking { (_currentPager as MultiParallelPager).initialize(); };
            else if(_currentPager is MultiPager<*>)
                (_currentPager as MultiPager).initialize();

            onPagerChanged.emit(_currentPager);
        }
    }

    private fun getCurrentSubPagers(): List<IPager<T>> {
        val reusableWindows = _pagersReusable.map { it.getWindow() };
        val placeholderWindows = synchronized(_pending) {
            _placeHolderPagersPaired.filter { _pending.contains(it.key) }.values
        }
        return reusableWindows + placeholderWindows;
    }

    override fun getCurrentPager(): IPager<T> {
        return synchronized(_pagersReusable) { _currentPager };
    }
}