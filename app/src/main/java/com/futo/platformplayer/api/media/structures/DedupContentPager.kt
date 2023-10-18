package com.futo.platformplayer.api.media.structures

import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.PlatformContentPlaceholder
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.getDiffDays
import com.futo.platformplayer.getNowDiffDays
import com.futo.platformplayer.logging.Logger
import com.futo.polycentric.core.combineHashCodes
import kotlin.math.abs

//TODO: If common pattern, create ModifierPager that implements all this composition
class DedupContentPager : IPager<IPlatformContent>, IAsyncPager<IPlatformContent>, IReplacerPager<IPlatformContent> {
    private val _basePager: IPager<IPlatformContent>;
    private val _pastResults: ArrayList<IPlatformContent> = arrayListOf();
    private var _currentResults: List<IPlatformContent>;

    private val _preferredPlatform: List<String>;

    override val onReplaced = Event2<IPlatformContent, IPlatformContent>();

    constructor(basePager: IPager<IPlatformContent>, preferredPlatform: List<String>? = null) {
        _preferredPlatform = preferredPlatform ?: listOf();
        _basePager = basePager;
        _currentResults = dedupResults(_basePager.getResults());
    }

    override fun hasMorePages(): Boolean =
        _basePager.hasMorePages();
    override fun nextPage() {
        _basePager.nextPage()
        _currentResults = dedupResults(_basePager.getResults());
    }

    override suspend fun nextPageAsync() {
        if(_basePager is IAsyncPager<*>)
            _basePager.nextPageAsync();
        else
            _basePager.nextPage();
        _currentResults = dedupResults(_basePager.getResults());
    }
    override fun getResults(): List<IPlatformContent> = _currentResults;

    private fun dedupResults(results: List<IPlatformContent>): List<IPlatformContent> {
        val resultsToRemove = arrayListOf<IPlatformContent>();

        for(result in results) {
            if(resultsToRemove.contains(result) || result is PlatformContentPlaceholder)
                continue;

            //TODO: Map allocation can prob be simplified to just index based.
            val sameItems = results.filter { isSameItem(result, it) };
            val platformItemMap = sameItems.groupBy { it.id.pluginId }.mapValues { (_, items) -> items.first() }
            val bestPlatform = _preferredPlatform.map { it.lowercase() }.firstOrNull { platformItemMap.containsKey(it) }
            val bestItem = platformItemMap[bestPlatform] ?: sameItems.first()

            resultsToRemove.addAll(sameItems.filter { it != bestItem });
        }
        val toReturn = results.filter { !resultsToRemove.contains(it) }.mapNotNull { item ->
            val olderItemIndex = _pastResults.indexOfFirst { isSameItem(item, it) };
            if(olderItemIndex >= 0) {
                val olderItem = _pastResults[olderItemIndex];
                val olderItemPriority = _preferredPlatform.indexOf(olderItem.id.pluginId);
                val newItemPriority = _preferredPlatform.indexOf(item.id.pluginId);
                if(newItemPriority < olderItemPriority) {
                    _pastResults[olderItemIndex] = item;
                    onReplaced.emit(olderItem, item);
                }
                return@mapNotNull null;
            }
            else
                return@mapNotNull item;
        };
        _pastResults.addAll(toReturn);
        return toReturn;
    }
    private fun isSameItem(item: IPlatformContent, item2: IPlatformContent): Boolean {
        return item.name == item2.name && (item.datetime == null || item2.datetime == null || abs(item.datetime!!.getDiffDays(item2.datetime!!)) < 2);
    }
    private fun calculateHash(item: IPlatformContent): Int {
        return combineHashCodes(listOf(item.name.hashCode(), item.datetime?.hashCode()));
    }

    companion object {
        private const val TAG = "DedupContentPager";
    }
}