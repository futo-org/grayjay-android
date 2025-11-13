package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow

open class JSAudioUrlSource(
    plugin: JSClient,
    obj: V8ValueObject
) : JSSource(TYPE_AUDIOURL, plugin, obj), IAudioUrlSource {

    private val ctx = "AudioUrlSource"
    private val cfg = plugin.config

    override val bitrate: Int =
        _obj.getOrThrow<Int>(cfg, "bitrate", ctx)

    override val container: String =
        _obj.getOrThrow<String>(cfg, "container", ctx)

    override val codec: String =
        _obj.getOrThrow<String>(cfg, "codec", ctx)

    private val url: String =
        _obj.getOrThrow<String>(cfg, "url", ctx)

    override val language: String =
        _obj.getOrThrow<String>(cfg, "language", ctx)

    override val duration: Long? =
        _obj.getOrDefault<Long>(cfg, "duration", ctx, null)?.toLong()

    override val name: String =
        _obj.getOrDefault<String>(cfg, "name", ctx, null)
            ?: "$container $bitrate"

    override var priority: Boolean =
        if (_obj.has("priority")) _obj.getOrThrow<Boolean>(cfg, "priority", ctx) else false

    override var original: Boolean =
        if (_obj.has("original")) _obj.getOrThrow<Boolean>(cfg, "original", ctx) else false

    override fun getAudioUrl(): String = url

    override fun toString(): String =
        "(name=$name, container=$container, bitrate=$bitrate, codec=$codec, url=$url, language=$language, duration=$duration)"
}
