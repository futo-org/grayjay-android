package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.IPluginSourced
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow

open class JSVideo : JSContent, IPlatformVideo, IPluginSourced {
    final override val contentType: ContentType get() = ContentType.MEDIA;

    final override val thumbnails: Thumbnails;

    final override val duration: Long;
    final override val viewCount: Long;

    final override val isLive: Boolean;
    final override val isShort: Boolean;

    constructor(config: SourcePluginConfig, obj: V8ValueObject): super(config, obj) {
        val contextName = "PlatformVideo";

        thumbnails = Thumbnails.fromV8(config, _content.getOrThrow(config, "thumbnails", contextName));

        duration = _content.getOrThrow<Int>(config, "duration", contextName).toLong();
        viewCount = _content.getOrThrow(config, "viewCount", contextName);
        isLive = _content.getOrThrow(config, "isLive", contextName);
        isShort = _content.getOrDefault(config, "isShort", contextName, false) ?: false;
    }
}