package com.futo.platformplayer.tv.channel

import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.structures.IAsyncPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.states.StatePlatform

internal data class TvChannelCapabilities(
    val types: List<String> = listOf(ResultCapabilities.TYPE_VIDEOS),
    val sorts: List<String> = emptyList(),
    val playlists: Boolean = false,
    val search: Boolean = false,
    val sourceName: String = ""
)

internal interface TvChannelSource {
    fun channel(url: String): IPlatformChannel

    fun capabilities(url: String): TvChannelCapabilities

    fun content(url: String, type: String?, sort: String?, query: String): IPager<IPlatformContent>

    fun playlists(url: String): IPager<IPlatformContent>

    fun playlist(url: String): IPager<IPlatformContent>

    companion object {
        val platform = object : TvChannelSource {
            override fun channel(url: String) = StatePlatform.instance.getChannelLive(url)

            override fun capabilities(url: String): TvChannelCapabilities {
                val client = StatePlatform.instance.getChannelClient(url)
                val caps = if (client.capabilities.hasGetChannelCapabilities) client.getChannelCapabilities() else null
                return TvChannelCapabilities(
                    types = caps?.types?.ifEmpty { null } ?: listOf(ResultCapabilities.TYPE_VIDEOS),
                    sorts = caps?.sorts.orEmpty(),
                    playlists = client.capabilities.hasGetChannelPlaylists,
                    search = client.capabilities.hasSearchChannelContents,
                    sourceName = client.name)
            }

            override fun content(
                url: String,
                type: String?,
                sort: String?,
                query: String
            ): IPager<IPlatformContent> {
                if (query.isNotBlank())
                    return StatePlatform.instance.getChannelClient(url).searchChannelContents(url, query, null, null, emptyMap())
                if (type == null && sort == null) return StatePlatform.instance.getChannelContent(url)
                return StatePlatform.instance.getChannelClient(url).getChannelContents(url, type, sort)
            }

            override fun playlists(url: String) = TvContentPager(StatePlatform.instance.getChannelPlaylists(url))

            override fun playlist(url: String) = TvContentPager(StatePlatform.instance.getPlaylist(url).contents)
        }
    }
}

internal class TvContentPager<T : IPlatformContent>(private val pager: IPager<T>) :
    IPager<IPlatformContent>, IAsyncPager<IPlatformContent> {
    override fun hasMorePages() = pager.hasMorePages()

    override fun getResults(): List<IPlatformContent> = pager.getResults()

    override fun nextPage() = pager.nextPage()

    override suspend fun nextPageAsync() {
        if (pager is IAsyncPager<*>) pager.nextPageAsync() else pager.nextPage()
    }
}
