package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow

open class JSAudioUrlSource : IAudioUrlSource, JSSource {
    override val name: String;
    override val bitrate : Int;
    override val container : String;
    override val codec: String;
    private val url : String;

    override val language: String;

    override val duration: Long?;

    override var priority: Boolean = false;

    override var original: Boolean = false;

    constructor(plugin: JSClient, obj: V8ValueObject) : super(TYPE_AUDIOURL, plugin, obj) {
        val contextName = "AudioUrlSource";
        val config = plugin.config;

        bitrate = _obj.getOrThrow(config, "bitrate", contextName);
        container = _obj.getOrThrow(config, "container", contextName);
        codec = _obj.getOrThrow(config, "codec", contextName);
        url = _obj.getOrThrow(config, "url", contextName);
        language = _obj.getOrThrow(config, "language", contextName);
        duration = _obj.getOrDefault(config, "duration", contextName, null);

        name = _obj.getOrDefault(config, "name", contextName, "${container} ${bitrate}") ?: "${container} ${bitrate}";

        priority = if(_obj.has("priority")) obj.getOrThrow(config, "priority", contextName) else false;
        original =  if(_obj.has("original")) obj.getOrThrow(config, "original", contextName) else false;
    }

    override fun getAudioUrl() : String {
        return url;
    }

    override fun toString(): String {
        return "(name=$name, container=$container, bitrate=$bitrate, codec=$codec, url=$url, language=$language, duration=$duration)";
    }
}