package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylistDetails
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.models.Playlist

class JSPlaylistDetails: JSPlaylist, IPlatformPlaylistDetails {
    override val contents: IPager<IPlatformVideo>;

    constructor(plugin: JSClient, config: SourcePluginConfig, obj: V8ValueObject): super(config, obj) {
        contents = JSVideoPager(config, plugin, obj.getOrThrow(config, "contents", "PlaylistDetails"));
    }

    override fun toPlaylist(): Playlist {
        val videos = contents.getResults().toMutableList();

        //Download all pages
        var allowedEmptyCount = 2;
        while(contents.hasMorePages()) {
            contents.nextPage();
            if(!videos.addAll(contents.getResults())) {
                allowedEmptyCount--;
                if(allowedEmptyCount <= 0)
                    break;
            }
            else allowedEmptyCount = 2;
        }

        return Playlist(id.toString(), name, videos.map { SerializedPlatformVideo.fromVideo(it)});
    }
}