package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrNull
import com.futo.platformplayer.getOrThrow

open class JSVideoUrlSource : IVideoUrlSource, JSSource {
    override val width : Int;
    override val height : Int;
    override val container : String;
    override val codec: String;
    override val name : String;
    override val bitrate : Int;
    override val duration: Long;
    private val url : String;

    override var priority: Boolean = false;

    constructor(plugin: JSClient, obj: V8ValueObject): super(TYPE_VIDEOURL, plugin, obj) {
        val contextName = "JSVideoUrlSource";
        val config = plugin.config;

        width = _obj.getOrThrow(config, "width", contextName);
        height = _obj.getOrThrow(config, "height", contextName);
        container = _obj.getOrThrow(config, "container", contextName);
        codec = _obj.getOrThrow(config, "codec", contextName);
        name = _obj.getOrThrow(config, "name", contextName);
        bitrate = _obj.getOrThrow(config, "bitrate", contextName);
        duration = _obj.getOrThrow<Int>(config, "duration", contextName).toLong();
        url = _obj.getOrThrow(config, "url", contextName);

        priority = obj.getOrNull(config, "priority", contextName) ?: false;
    }

    override fun getVideoUrl() : String {
        return url;
    }

    override fun toString(): String {
        return "(width=$width, height=$height, container=$container, codec=$codec, name=$name, bitrate=$bitrate, duration=$duration, url=$url)"
    }
}