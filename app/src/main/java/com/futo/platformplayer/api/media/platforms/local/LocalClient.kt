package com.futo.platformplayer.api.media.platforms.local

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import com.futo.platformplayer.R
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.PlatformClientCapabilities
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
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
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.structures.EmptyPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.engine.exceptions.ScriptUnavailableException
import com.futo.platformplayer.models.ImageVariable
import com.futo.platformplayer.states.StateLibrary
import java.net.MalformedURLException

class LocalClient: IPlatformClient {
    override val id: String  = "LOCAL"
    override val name: String = "Local"
    override val icon: ImageVariable? = ImageVariable.fromResource(R.drawable.ic_library)
    override val capabilities: PlatformClientCapabilities = PlatformClientCapabilities()

    override fun initialize() {}

    override fun disable() {

    }

    override fun getHome(): IPager<IPlatformContent>
        = EmptyPager();

    override fun isContentDetailsUrl(url: String): Boolean {
        try {
            val uri = Uri.parse(url);
            return ContentResolver.SCHEME_CONTENT == uri.scheme
                    && MediaStore.AUTHORITY == uri.authority;
        }
        catch(ex: MalformedURLException) {
            return false;
        }
    }
    override fun getContentDetails(url: String): IPlatformContentDetails {
        val uri = Uri.parse(url);

        if("audio" in uri.pathSegments) {
            return StateLibrary.getAudioTrack(url) ?: throw Exception("Failed to find ${url}");
        }
        else if("video" in uri.pathSegments) {
            return StateLibrary.getVideoTrack(url) ?: throw Exception("Failed to find ${url}");
        }
        else
            throw Exception("Unknown content url [${url}]");
    }

    override fun getSearchCapabilities(): ResultCapabilities
        = ResultCapabilities();
    override fun search(query: String, type: String?, order: String?, filters: Map<String, List<String>>?): IPager<IPlatformContent> {
        return EmptyPager(); //TODO
    }

    override fun getSearchChannelContentsCapabilities(): ResultCapabilities
        = ResultCapabilities();
    override fun searchChannelContents(channelUrl: String, query: String, type: String?, order: String?, filters: Map<String, List<String>>?): IPager<IPlatformContent> {
        return EmptyPager(); //TODO
    }

    override fun searchChannels(query: String): IPager<PlatformAuthorLink> {
        return EmptyPager(); //TODO
    }

    override fun searchChannelsAsContent(query: String): IPager<IPlatformContent> {
        return EmptyPager(); //TODO
    }

    override fun isChannelUrl(url: String): Boolean {
        return false //TODO
    }

    override fun getChannel(channelUrl: String): IPlatformChannel {
        throw NotImplementedError();
    }

    override fun getChannelCapabilities(): ResultCapabilities
        = ResultCapabilities();
    override fun getChannelContents(channelUrl: String, type: String?, order: String?, filters: Map<String, List<String>>?): IPager<IPlatformContent> {
        return EmptyPager();
    }

    override fun getChannelPlaylists(channelUrl: String): IPager<IPlatformPlaylist> {
        return EmptyPager();
    }

    override fun getPeekChannelTypes(): List<String> = listOf();

    override fun peekChannelContents(channelUrl: String, type: String?): List<IPlatformContent>
        = listOf();

    override fun getShorts(): IPager<IPlatformVideo> = EmptyPager();

    override fun searchSuggestions(query: String): Array<String> = arrayOf();

    override fun getChannelUrlByClaim(claimType: Int, claimValues: Map<Int, String>): String?
            = null;

    override fun getContentChapters(url: String): List<IChapter>
        = listOf();

    override fun getPlaybackTracker(url: String): IPlaybackTracker?
        = null;

    override fun getContentRecommendations(url: String): IPager<IPlatformContent>?
        = null;

    override fun getComments(url: String): IPager<IPlatformComment>
        = EmptyPager();

    override fun getSubComments(comment: IPlatformComment): IPager<IPlatformComment>
        = EmptyPager();

    override fun getLiveChatWindow(url: String): ILiveChatWindowDescriptor?
        = null;

    override fun getLiveEvents(url: String): IPager<IPlatformLiveEvent>?
        = null;

    override fun searchPlaylists(query: String, type: String?, order: String?, filters: Map<String, List<String>>?): IPager<IPlatformContent>
        = throw NotImplementedError();

    override fun isPlaylistUrl(url: String): Boolean = false;

    override fun getPlaylist(url: String): IPlatformPlaylistDetails
        = throw NotImplementedError();
    override fun getUserPlaylists(): Array<String> = throw NotImplementedError();
    override fun getUserSubscriptions(): Array<String> = throw NotImplementedError();
    override fun getUserHistory(): IPager<IPlatformContent> = throw NotImplementedError();
    override fun isClaimTypeSupported(claimType: Int): Boolean = false;
}