package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.locked.IPlatformLockedContent
import com.futo.platformplayer.api.media.models.nested.IPlatformNestedContent
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.states.StatePlatform

//TODO: Refactor into video-only
class JSLockedContent: IPlatformLockedContent, JSContent {

    override val contentType: ContentType get() = ContentType.LOCKED;
    override val lockContentType: ContentType get() = ContentType.MEDIA;

    override val lockDescription: String?;
    override val unlockUrl: String?;

    override val contentName: String?;
    override val contentThumbnails: Thumbnails;

    constructor(config: SourcePluginConfig, obj: V8ValueObject): super(config, obj) {
        val contextName = "PlatformLockedContent";

        this.contentName = obj.getOrDefault(config, "contentName", contextName, null);
        this.contentThumbnails = obj.getOrDefault<V8ValueObject?>(config, "contentThumbnails", contextName, null)?.let {
            return@let Thumbnails.fromV8(config, it);
        } ?: Thumbnails();

        lockDescription = obj.getOrDefault(config, "lockDescription", contextName, null);
        unlockUrl = obj.getOrDefault(config, "unlockUrl", contextName, null);
    }
}