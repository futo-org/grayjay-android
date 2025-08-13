package com.futo.platformplayer.api.media.structures

import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import java.util.stream.IntStream

/**
 * A Content MultiPager that returns results based on a specified distribution
 * TODO: Merge all basic distribution pagers
 */
class MultiDistributionContentPager<T : IPlatformContent> : MultiPager<T> {

    private val dist : HashMap<IPager<T>, Float>;
    private val distConsumed : HashMap<IPager<T>, Float>;

    constructor(pagers : Map<IPager<T>, Float>, pageSize: Int = 9) : super(pagers.keys.toMutableList(), false, pageSize) {
        val distTotal = pagers.values.sum();
        dist = HashMap();

        //Convert distribution values to inverted percentages
        for(kv in pagers)
            dist[kv.key] = 1f - (kv.value / distTotal);
        distConsumed = HashMap();
        for(kv in dist)
            distConsumed[kv.key] = 0f;
    }

    @Synchronized
    override fun selectItemIndex(options: Array<SelectionOption<T>>): Int {
        if(options.size == 0)
            return -1;
        var bestIndex = 0;
        var bestConsumed = distConsumed[options[0].pager.getPager()]!! + dist[options[0].pager.getPager()]!!;
        for(i in IntStream.range(1, options.size)) {
            val pager = options[i].pager.getPager();
            val valueAfterAdd = distConsumed[pager]!! + dist[pager]!!;

            if(valueAfterAdd < bestConsumed) {
                bestIndex = i;
                bestConsumed = valueAfterAdd;
            }
        }
        distConsumed[options[bestIndex].pager.getPager()] = bestConsumed;
        return bestIndex;
    }
}