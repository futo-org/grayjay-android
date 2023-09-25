package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.nested.IPlatformNestedContent
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.states.StatePlatform

//TODO: Refactor into video-only
class JSNestedMediaContent: IPlatformNestedContent, JSContent {

    override val contentType: ContentType get() = ContentType.NESTED_VIDEO;
    override val nestedContentType: ContentType get() = ContentType.MEDIA;

    override val contentUrl: String;
    override val contentName: String?;
    override val contentDescription: String?;
    override val contentProvider: String?;
    override val contentThumbnails: Thumbnails;

    override val contentPlugin: String?;
    override val contentSupported: Boolean get() = contentPlugin != null && StatePlatform.instance.isClientEnabled(contentPlugin);

    constructor(config: SourcePluginConfig, obj: V8ValueObject): super(config, obj) {
        val contextName = "PlatformNestedContent";

        this.contentUrl = obj.getOrThrow(config, "contentUrl", contextName);
        this.contentName = obj.getOrDefault(config, "contentName", contextName, null);
        this.contentDescription = obj.getOrDefault(config, "contentName", contextName, null);
        this.contentProvider = obj.getOrDefault(config, "contentName", contextName, null);
        this.contentThumbnails = obj.getOrDefault<V8ValueObject?>(config, "contentThumbnails", contextName, null)?.let {
            return@let Thumbnails.fromV8(config, it);
        } ?: Thumbnails();
        this.contentPlugin = StatePlatform.instance.getContentClientOrNull(this.contentUrl)?.id;
    }
}