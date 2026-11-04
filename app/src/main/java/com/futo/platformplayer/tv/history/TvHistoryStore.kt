package com.futo.platformplayer.tv.history

import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.models.HistoryVideo
import com.futo.platformplayer.states.StateHistory
import com.futo.platformplayer.states.StatePlatform

internal interface TvHistoryStore {
    suspend fun search(query: String): IPager<HistoryVideo>

    suspend fun remove(url: String)

    suspend fun clear(minutes: Long)

    suspend fun canPlay(url: String): Boolean

    companion object {
        fun platform(): TvHistoryStore =
            object : TvHistoryStore {
                override suspend fun search(query: String) =
                    if (query.isBlank()) StateHistory.instance.getHistoryPager()
                    else StateHistory.instance.getHistorySearchPager(query)

                override suspend fun remove(url: String) = StateHistory.instance.removeHistory(url)

                override suspend fun clear(minutes: Long) = StateHistory.instance.removeHistoryRange(minutes)

                override suspend fun canPlay(url: String) =
                    url.startsWith("content://") ||
                        url.startsWith("file://") ||
                        StatePlatform.instance.getContentClientOrNull(url) != null
            }

        fun watched(item: HistoryVideo) = item.video.duration > 0 && item.position > item.video.duration * .7
    }
}
