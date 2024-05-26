package com.futo.platformplayer.api.media

import androidx.collection.LruCache
import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.chapters.IChapter
import com.futo.platformplayer.api.media.models.comments.IPlatformComment
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.live.ILiveChatWindowDescriptor
import com.futo.platformplayer.api.media.models.live.IPlatformLiveEvent
import com.futo.platformplayer.api.media.models.playback.IPlaybackTracker
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylistDetails
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.models.ImageVariable

/**
 * A temporary class that caches video results
 * In future this should be part of a bigger system
 */
class CachedPlatformClient : IPlatformClient {
    private val _client : IPlatformClient;
    override val id: String get() = _client.id;
    override val name: String get() = _client.name;
    override val icon: ImageVariable? get() = _client.icon;

    private val _cache: LruCache<String, IPlatformContentDetails>;

    override val capabilities: PlatformClientCapabilities
        get() = _client.capabilities;

    constructor(client : IPlatformClient, cacheSize : Int = 10 * 1024 * 1024) {
        this._client = client;
        this._cache = LruCache<String, IPlatformContentDetails>(cacheSize);
    }
    override fun initialize() { _client.initialize() }
    override fun disable() { _client.disable() }

    override fun isContentDetailsUrl(url: String): Boolean = _client.isContentDetailsUrl(url);
    override fun getContentDetails(url: String): IPlatformContentDetails {
        var result = _cache.get(url);
        if(result == null) {
            result = _client.getContentDetails(url);
            _cache.put(url, result);
        }
        return result;
    }

    override fun getContentChapters(url: String): List<IChapter> = _client.getContentChapters(url);
    override fun getPlaybackTracker(url: String): IPlaybackTracker? = _client.getPlaybackTracker(url);

    override fun isChannelUrl(url: String): Boolean = _client.isChannelUrl(url);
    override fun getChannel(channelUrl: String): IPlatformChannel = _client.getChannel(channelUrl);

    override fun getChannelCapabilities(): ResultCapabilities  = _client.getChannelCapabilities();
    override fun getChannelContents(
        channelUrl: String,
        type: String?,
        order: String?,
        filters: Map<String, List<String>>?
    ): IPager<IPlatformContent> = _client.getChannelContents(channelUrl);

    override fun getChannelPlaylists(channelUrl: String): IPager<IPlatformPlaylist> = _client.getChannelPlaylists(channelUrl);

    override fun getPeekChannelTypes(): List<String> = _client.getPeekChannelTypes();
    override fun peekChannelContents(channelUrl: String, type: String?): List<IPlatformContent> = _client.peekChannelContents(channelUrl, type);

    override fun getChannelUrlByClaim(claimType: Int, claimValues: Map<Int, String>): String? = _client.getChannelUrlByClaim(claimType, claimValues)

    override fun searchSuggestions(query: String): Array<String> = _client.searchSuggestions(query);
    override fun getSearchCapabilities(): ResultCapabilities  = _client.getSearchCapabilities();
    override fun search(
        query: String,
        type: String?,
        order: String?,
        filters: Map<String, List<String>>?
    ): IPager<IPlatformContent> = _client.search(query, type, order, filters);

    override fun getSearchChannelContentsCapabilities(): ResultCapabilities = _client.getSearchChannelContentsCapabilities();
    override fun searchChannelContents(
        channelUrl: String,
        query: String,
        type: String?,
        order: String?,
        filters: Map<String, List<String>>?
    ): IPager<IPlatformContent> = _client.searchChannelContents(channelUrl, query, type, order, filters);

    override fun searchChannels(query: String) = _client.searchChannels(query);

    override fun getComments(url: String): IPager<IPlatformComment> = _client.getComments(url);
    override fun getSubComments(comment: IPlatformComment): IPager<IPlatformComment> = _client.getSubComments(comment);

    override fun getLiveChatWindow(url: String): ILiveChatWindowDescriptor? = _client.getLiveChatWindow(url);
    override fun getLiveEvents(url: String): IPager<IPlatformLiveEvent>? = _client.getLiveEvents(url);

    override fun getHome(): IPager<IPlatformContent> = _client.getHome();

    override fun getUserSubscriptions(): Array<String> { return arrayOf(); };

    override fun searchPlaylists(query: String, type: String?, order: String?, filters: Map<String, List<String>>?): IPager<IPlatformContent> = _client.searchPlaylists(query, type, order, filters);
    override fun isPlaylistUrl(url: String): Boolean = _client.isPlaylistUrl(url);
    override fun getPlaylist(url: String): IPlatformPlaylistDetails = _client.getPlaylist(url);
    override fun getUserPlaylists(): Array<String> { return arrayOf(); };

    override fun isClaimTypeSupported(claimType: Int): Boolean {
        return _client.isClaimTypeSupported(claimType);
    }
}