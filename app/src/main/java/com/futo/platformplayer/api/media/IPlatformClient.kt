package com.futo.platformplayer.api.media

import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
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
import com.futo.platformplayer.models.Playlist

/**
 * A client for a specific platform
 */
interface IPlatformClient {
    val id: String;
    val name: String;

    val icon: ImageVariable?;

    //Capabilities
    val capabilities: PlatformClientCapabilities;

    fun initialize();
    fun disable();

    /**
     * Gets the home recommendations
     */
    fun getHome(): IPager<IPlatformContent>

    //Search
    /**
     * Gets search suggestion for the provided query string
     */
    fun searchSuggestions(query: String): Array<String>;
    /**
     * Describes what the plugin is capable on filtering/sorting search results
     */
    fun getSearchCapabilities(): ResultCapabilities;
    /**
     * Searches for content and returns a search pager with results
     */
    fun search(query: String, type: String? = null, order: String? = null, filters: Map<String, List<String>>? = null): IPager<IPlatformContent>;



    /**
     * Describes what the plugin is capable on filtering/sorting search results on channels
     */
    fun getSearchChannelContentsCapabilities(): ResultCapabilities;
    /**
     * Searches for content on a channel and returns a video pager
     */
    fun searchChannelContents(channelUrl: String, query: String, type: String? = null, order: String? = null, filters: Map<String, List<String>>? = null): IPager<IPlatformContent>;


    /**
     * Searches for channels and returns a channel pager
     */
    fun searchChannels(query: String): IPager<PlatformAuthorLink>;


    //Video Pages
    /**
     * Determines if the provided url is a valid url for getting channel from this client
     */
    fun isChannelUrl(url: String): Boolean;
    /**
     * Gets channel details, might also fetch videos which is then obtained by IPlatformChannel.getVideos. Otherwise might fall back to getChannelVideos
     */
    fun getChannel(channelUrl: String): IPlatformChannel;
    /**
     * Describes what the plugin is capable on filtering/sorting channel results
     */
    fun getChannelCapabilities(): ResultCapabilities;
    /**
     * Gets all videos of a channel, ideally in upload time descending
     */
    fun getChannelContents(channelUrl: String, type: String? = null, order: String? = null, filters: Map<String, List<String>>? = null): IPager<IPlatformContent>;

    /**
     * Gets the channel url associated with a claimType
     */
    fun getChannelUrlByClaim(claimType: Int, claimValues: Map<Int, String>): String?;

    //Video
    /**
     * Determines if the provided url is a valid url for getting details from this client
     */
    fun isContentDetailsUrl(url: String): Boolean;
    /**
     * Gets the video details for a given url, including video/audio streams
     */
    fun getContentDetails(url: String): IPlatformContentDetails;

    /**
     * Gets the playback tracker for a piece of content
     */
    fun getPlaybackTracker(url: String): IPlaybackTracker?;


    //Comments
    /**
     * Gets the comments underneath a video
     */
    fun getComments(url: String): IPager<IPlatformComment>;
    /**
     * Gets the replies to a comment
     */
    fun getSubComments(comment: IPlatformComment): IPager<IPlatformComment>;

    /**
     * Gets the live events of a livestream
     */
    fun getLiveChatWindow(url: String): ILiveChatWindowDescriptor?;
    /**
     * Gets the live events of a livestream
     */
    fun getLiveEvents(url: String): IPager<IPlatformLiveEvent>?


    //Playlists
    /**
     * Search for Playlists and returns a Playlist pager
     */
    fun searchPlaylists(query: String, type: String? = null, order: String? = null, filters: Map<String, List<String>>? = null): IPager<IPlatformContent>;
    /**
     * Gets a playlist from a url
     */
    fun isPlaylistUrl(url: String): Boolean;
    /**
     * Gets a playlist from a url
     */
    fun getPlaylist(url: String): IPlatformPlaylistDetails;

    //Migration
    /**
     * Retrieves the playlists of the currently logged in user
     */
    fun getUserPlaylists(): Array<String>;
    /**
     * Retrieves the subscriptions of the currently logged in user
     */
    fun getUserSubscriptions(): Array<String>;


    fun isClaimTypeSupported(claimType: Int): Boolean;
}