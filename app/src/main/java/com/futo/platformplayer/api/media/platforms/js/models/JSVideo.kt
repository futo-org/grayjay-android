package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.IPluginSourced
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.serializers.OffsetDateTimeNullableSerializer
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

open class JSVideo : JSContent, IPlatformVideo, IPluginSourced {
    final override val contentType: ContentType get() = ContentType.MEDIA;

    final override val thumbnails: Thumbnails;

    final override val duration: Long;
    final override val viewCount: Long;

    override var playbackTime: Long = -1;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeNullableSerializer::class)
    override var playbackDate: OffsetDateTime? = null;

    final override val isLive: Boolean;
    final override val isShort: Boolean;

    constructor(config: SourcePluginConfig, obj: V8ValueObject): super(config, obj) {
        val contextName = "PlatformVideo";

        thumbnails = Thumbnails.fromV8(config, _content.getOrThrow(config, "thumbnails", contextName));

        duration = _content.getOrThrow<Int>(config, "duration", contextName).toLong();
        viewCount = _content.getOrThrow(config, "viewCount", contextName);
        isLive = _content.getOrThrow(config, "isLive", contextName);
        isShort = _content.getOrDefault(config, "isShort", contextName, false) ?: false;
        playbackTime = _content.getOrDefault<Long>(config, "playbackTime", contextName, -1)?.toLong() ?: -1;
        val playbackDateInt = _content.getOrDefault<Int>(config, "playbackDate", contextName, null)?.toLong();
        if(playbackDateInt == null || playbackDateInt == 0.toLong())
            playbackDate = null;
        else
            playbackDate = OffsetDateTime.of(LocalDateTime.ofEpochSecond(playbackDateInt, 0, ZoneOffset.UTC), ZoneOffset.UTC);
    }
}