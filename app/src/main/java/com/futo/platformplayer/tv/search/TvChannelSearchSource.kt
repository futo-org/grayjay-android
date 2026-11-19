package com.futo.platformplayer.tv.search

import com.futo.platformplayer.tv.channel.TvChannelSource

internal class TvChannelSearchSource(
    private val url: String,
    private val channel: TvChannelSource
) : TvSearchSource by TvSearchSource.platform {
    override suspend fun providers(): List<TvSearchProvider> {
        val capabilities = channel.capabilities(url)
        return listOf(TvSearchProvider(
            url,
            capabilities.sourceName,
            if (capabilities.search) setOf(TvSearchKind.VIDEOS) else emptySet()))
    }

    override suspend fun search(query: String, kind: TvSearchKind, options: TvSearchOptions): TvSearchResults {
        require(kind == TvSearchKind.VIDEOS)
        return TvSearchResults(channel.content(url, null, null, query))
    }

    override suspend fun suggestions(query: String) = emptyList<String>()

    override fun recent() = emptyList<String>()

    override fun remember(query: String) = Unit

    override fun forget(query: String) = Unit
}
