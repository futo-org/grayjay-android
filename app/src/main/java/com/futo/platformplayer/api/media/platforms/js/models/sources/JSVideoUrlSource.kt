package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrNull
import com.futo.platformplayer.getOrThrow

open class JSVideoUrlSource(
    plugin: JSClient,
    obj: V8ValueObject
) : JSSource(TYPE_VIDEOURL, plugin, obj), IVideoUrlSource {

    private val ctx = "JSVideoUrlSource"
    private val cfg = plugin.config

    override val width: Int =
        _obj.getOrThrow<Int>(cfg, "width", ctx)

    override val height: Int =
        _obj.getOrThrow<Int>(cfg, "height", ctx)

    override val container: String =
        _obj.getOrThrow<String>(cfg, "container", ctx)

    override val codec: String =
        _obj.getOrThrow<String>(cfg, "codec", ctx)

    override val name: String =
        _obj.getOrThrow<String>(cfg, "name", ctx)

    override val bitrate: Int =
        _obj.getOrThrow<Int>(cfg, "bitrate", ctx)

    override val duration: Long =
        _obj.getOrThrow<Long>(cfg, "duration", ctx)

    private val url: String =
        _obj.getOrThrow<String>(cfg, "url", ctx)

    override var priority: Boolean =
        _obj.getOrDefault<Boolean>(cfg, "priority", ctx, false) ?: false

    override val language: String? = _obj.getOrDefault(cfg, "language", ctx, null);
    override val original: Boolean? = _obj.getOrDefault(cfg, "original", ctx, null);

    override fun getVideoUrl(): String = url

    override fun toString(): String =
        "(width=$width, height=$height, container=$container, codec=$codec, name=$name, bitrate=$bitrate, duration=$duration, url=$url)"
}
