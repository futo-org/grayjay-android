package com.futo.platformplayer.api.media.structures

import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import kotlinx.coroutines.runBlocking
import java.util.stream.IntStream


/**
 * A Content AsyncMultiPager that returns results based on a specified distribution
 * Unlike its non-async counterpart, this one uses parallel nextPage requests
 */
class MultiChronoContentParallelPager : MultiParallelPager<IPlatformContent> {

    constructor(pagers: List<IPager<IPlatformContent>>) : super(pagers)

    @Synchronized
    override fun selectItemIndex(options: Array<SelectionOption<IPlatformContent>>): Int {
        if(options.size == 0)
            return -1;
        var bestIndex = 0;

        val allResults = runBlocking { options.map { Pair(it, it.item?.await()) } };
        for(i in IntStream.range(1, options.size)) {
            val best = allResults[bestIndex].second;
            val cur = allResults[i].second ?: continue;
            if(best?.datetime == null || (cur.datetime != null && cur.datetime!! > best.datetime!!))
                bestIndex = i;
        }
        return bestIndex;
    }


}