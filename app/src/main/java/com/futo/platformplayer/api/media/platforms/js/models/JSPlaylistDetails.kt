package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylistDetails
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.api.media.structures.ReusablePager
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.models.Playlist
import java.util.UUID

class JSPlaylistDetails: JSPlaylist, IPlatformPlaylistDetails {
    override val contents: IPager<IPlatformVideo>;

    constructor(plugin: JSClient, config: SourcePluginConfig, obj: V8ValueObject): super(config, obj) {
        contents = ReusablePager(JSVideoPager(config, plugin, obj.getOrThrow(config, "contents", "PlaylistDetails")));
    }

    override fun toPlaylist(onProgress: ((progress: Int) -> Unit)?): Playlist {
        val playlist = if (contents is ReusablePager) contents.getWindow() else contents;
        val videos = playlist.getResults().toMutableList();
        onProgress?.invoke(videos.size);

        //Download all pages
        var allowedEmptyCount = 2;
        while(playlist.hasMorePages()) {
            playlist.nextPage();
            if(!videos.addAll(playlist.getResults())) {
                allowedEmptyCount--;
                if(allowedEmptyCount <= 0)
                    break;
            }
            else allowedEmptyCount = 2;

            onProgress?.invoke(videos.size);
        }

        return Playlist(UUID.randomUUID().toString(), name, videos.map { SerializedPlatformVideo.fromVideo(it)});
    }
}