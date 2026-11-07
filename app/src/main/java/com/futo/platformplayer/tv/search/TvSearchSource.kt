package com.futo.platformplayer.tv.search

import com.futo.platformplayer.R
import com.futo.platformplayer.Settings
import com.futo.platformplayer.api.media.models.FilterGroup
import com.futo.platformplayer.api.media.models.IPlatformChannelContent
import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.api.media.structures.MultiDistributionContentPager
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateMeta
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.stores.FragmentedStorage
import com.futo.platformplayer.stores.SearchHistoryStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal enum class TvSearchKind(val label: Int, val viewId: Int) {
    VIDEOS(R.string.videos, R.id.tv_search_videos),
    CHANNELS(R.string.tv_search_channels, R.id.tv_search_channels),
    PLAYLISTS(R.string.playlists, R.id.tv_search_playlists);

    fun accepts(content: IPlatformContent) =
        when (this) {
            VIDEOS -> content is IPlatformVideo
            CHANNELS -> content is IPlatformChannelContent
            PLAYLISTS -> content is IPlatformPlaylist
        }
}

internal data class TvSearchProvider(
    val id: String,
    val name: String,
    val kinds: Set<TvSearchKind>,
    val capabilities: ResultCapabilities = ResultCapabilities()
)

internal data class TvSearchOptions(
    val sourceIds: Set<String>? = null,
    val sort: String? = null,
    val filters: Map<String, List<String>> = emptyMap()
) {
    fun providers(catalog: List<TvSearchProvider>, kind: TvSearchKind) = catalog.filter {
        kind in it.kinds && (sourceIds == null || it.id in sourceIds)
    }

    fun capabilities(catalog: List<TvSearchProvider>, kind: TvSearchKind): ResultCapabilities {
        if (kind != TvSearchKind.VIDEOS) return ResultCapabilities()
        val caps = providers(catalog, kind).map { it.capabilities }
        val first = caps.firstOrNull() ?: return ResultCapabilities()
        val sorts = first.sorts.filter { sort -> caps.all { sort in it.sorts } }
        val groups = first.filters.mapNotNull { group ->
            val matches = caps.map { cap ->
                cap.filters.find { it.idOrName == group.idOrName } ?: return@mapNotNull null
            }
            val values = group.filters.filter { value ->
                matches.all { match -> match.filters.any { it.idOrName == value.idOrName } }
            }
            if (values.isEmpty()) null
            else FilterGroup(group.name, values, matches.all { it.isMultiSelect }, group.id)
        }
        return ResultCapabilities(sorts = sorts, filters = groups)
    }

    fun sanitized(catalog: List<TvSearchProvider>, kind: TvSearchKind): TvSearchOptions {
        val caps = capabilities(catalog, kind)
        return copy(
            sort = sort?.takeIf { it in caps.sorts },
            filters =
                caps.filters
                    .mapNotNull { group ->
                        val values = filters[group.idOrName]
                            .orEmpty()
                            .filter { value -> group.filters.any { it.idOrName == value } }
                            .let { if (group.isMultiSelect) it else it.take(1) }
                        if (values.isEmpty()) null else group.idOrName to values
                    }
                    .toMap())
    }
}

internal data class TvSearchResults(val pager: IPager<IPlatformContent>, val failedSources: List<String> = emptyList())

internal enum class TvSearchUrl {
    VIDEO,
    CHANNEL,
    PLAYLIST,
    UNSUPPORTED
}

internal interface TvSearchSource {
    suspend fun providers(): List<TvSearchProvider>

    suspend fun suggestions(query: String): List<String>

    suspend fun search(query: String, kind: TvSearchKind, options: TvSearchOptions): TvSearchResults

    suspend fun resolveUrl(url: String): TvSearchUrl

    fun recent(): List<String>

    fun remember(query: String)

    fun forget(query: String)

    fun visible(content: IPlatformContent): Boolean = true

    companion object {
        val platform = object : TvSearchSource {
            override suspend fun providers() =
                clients().map { client ->
                    val caps = try {
                        if (client.capabilities.hasGetSearchCapabilities) client.getSearchCapabilities()
                        else ResultCapabilities()
                    } catch (error: Exception) {
                        Logger.w("TvSearch", "Search capabilities unavailable for ${client.name}", error)
                        ResultCapabilities()
                    }
                    TvSearchProvider(
                        client.id,
                        client.name,
                        buildSet {
                            add(TvSearchKind.VIDEOS)
                            if (client.capabilities.hasChannelSearch) add(TvSearchKind.CHANNELS)
                            if (client.capabilities.hasSearchPlaylists) add(TvSearchKind.PLAYLISTS)
                        },
                        caps)
                }

            override suspend fun suggestions(query: String) =
                clients().firstOrNull()?.searchSuggestions(query)?.toList().orEmpty()

            override suspend fun search(
                query: String,
                kind: TvSearchKind,
                options: TvSearchOptions
            ): TvSearchResults = coroutineScope {
                val selected = clients().filter { client ->
                    (options.sourceIds == null || client.id in options.sourceIds) &&
                        when (kind) {
                            TvSearchKind.VIDEOS -> true
                            TvSearchKind.CHANNELS -> client.capabilities.hasChannelSearch
                            TvSearchKind.PLAYLISTS -> client.capabilities.hasSearchPlaylists
                        }
                }
                check(selected.isNotEmpty()) { "No compatible search sources" }
                val results = selected.map { client ->
                    async(Dispatchers.IO) {
                        client.name to
                            try {
                                val pager = when (kind) {
                                    TvSearchKind.VIDEOS -> {
                                        val caps = if (options.filters.isEmpty()) ResultCapabilities()
                                        else client.getSearchCapabilities()
                                        val filters = options.filters.mapValues { (group, values) ->
                                            val supported = caps.filters.first { it.idOrName == group }
                                            values.map { value ->
                                                supported.filters
                                                    .first { it.idOrName == value }
                                                    .value
                                            }
                                        }
                                        client.search(query, null, options.sort, filters)
                                    }
                                    TvSearchKind.CHANNELS -> client.searchChannelsAsContent(query)
                                    TvSearchKind.PLAYLISTS -> client.searchPlaylists(query)
                                }
                                Result.success(pager)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Logger.w("TvSearch", "Search failed for ${client.name}", error)
                                Result.failure(error)
                            }
                    }
                }
                    .awaitAll()
                val successful = results.mapNotNull { it.second.getOrNull() }
                if (successful.isEmpty()) throw results.first().second.exceptionOrNull()!!
                val pager = if (successful.size == 1) successful.first()
                else MultiDistributionContentPager(successful.associateWith { 1f }).also { it.initialize() }
                TvSearchResults(pager, results.filter { it.second.isFailure }.map { it.first })
            }

            override suspend fun resolveUrl(url: String) =
                when {
                    StatePlatform.instance.hasEnabledPlaylistClient(url) -> TvSearchUrl.PLAYLIST
                    StatePlatform.instance.hasEnabledChannelClient(url) -> TvSearchUrl.CHANNEL
                    StatePlatform.instance.hasEnabledContentClient(url) -> TvSearchUrl.VIDEO
                    else -> TvSearchUrl.UNSUPPORTED
                }

            override fun recent() =
                if (Settings.instance.search.searchHistory && !StateApp.instance.privateMode)
                    FragmentedStorage.get<SearchHistoryStorage>().lastQueries.toList()
                else emptyList()

            override fun remember(query: String) {
                if (Settings.instance.search.searchHistory && !StateApp.instance.privateMode)
                    FragmentedStorage.get<SearchHistoryStorage>().add(query)
            }

            override fun forget(query: String) {
                FragmentedStorage.get<SearchHistoryStorage>().apply {
                    lastQueries.remove(query)
                    save()
                }
            }

            override fun visible(content: IPlatformContent): Boolean {
                if (!Settings.instance.search.hidefromSearch) return true
                return !StateMeta.instance.isVideoHidden(content.url) &&
                    !StateMeta.instance.isCreatorHidden(
                        if (content is IPlatformChannelContent) content.url else content.author.url)
            }

            private fun clients() =
                StatePlatform.instance.getSortedEnabledClient().filter { it !is JSClient || it.enableInSearch }
        }
    }
}
