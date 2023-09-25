package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.getOrDefault

open class JSPlaylist : JSContent, IPlatformPlaylist {
    override val contentType: ContentType get() = ContentType.PLAYLIST;
    override val thumbnail: String?;
    override val videoCount: Int;

    constructor(config: SourcePluginConfig, obj: V8ValueObject) : super(config, obj) {
        val contextName = "Playlist";
        thumbnail = obj.getOrDefault(config, "thumbnail", contextName, null);
        videoCount = obj.getOrDefault(config, "videoCount", contextName, 0)!!;
    }
}