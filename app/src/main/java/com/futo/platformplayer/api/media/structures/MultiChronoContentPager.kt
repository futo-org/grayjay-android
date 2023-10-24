package com.futo.platformplayer.api.media.structures

import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import java.util.stream.IntStream

/**
 * A PlatformContent MultiPager that orders the results of a page based on the datetime of a content item
 */
class MultiChronoContentPager : MultiPager<IPlatformContent> {
    constructor(pagers : Array<IPager<IPlatformContent>>, allowFailure: Boolean = false, pageSize: Int = 9) : super(pagers.map { it }.toList(), allowFailure, pageSize) {}
    constructor(pagers : List<IPager<IPlatformContent>>, allowFailure: Boolean = false, pageSize: Int = 9) : super(pagers, allowFailure, pageSize) {}

    @Synchronized
    override fun selectItemIndex(options: Array<SelectionOption<IPlatformContent>>): Int {
        if(options.size == 0)
            return -1;
        var bestIndex = 0;
        for(i in IntStream.range(1, options.size)) {
            val best = options[bestIndex].item!!;
            val cur = options[i].item!!;
            if(best.datetime == null || (cur.datetime != null && cur.datetime!! > best.datetime!!))
                bestIndex = i;
        }
        return bestIndex;
    }
}