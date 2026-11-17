package com.futo.platformplayer.tv.playlists

import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.structures.IAsyncPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.tv.channel.TvChannelSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class TvRemotePlaylistQueue(
    private val url: String,
    private val source: TvChannelSource = TvChannelSource.platform
) {
    private var pager: IPager<IPlatformContent>? = null

    var exhausted = false
        private set

    suspend fun next(): List<IPlatformVideo> {
        val result = withContext(Dispatchers.IO) {
            val page = pager?.also {
                if (it is IAsyncPager<*>) it.nextPageAsync() else it.nextPage()
            } ?: source.playlist(url)
            val videos = page.getResults().filterIsInstance<IPlatformVideo>()
            pager = page
            videos to !page.hasMorePages()
        }
        exhausted = result.second
        return result.first
    }
}
