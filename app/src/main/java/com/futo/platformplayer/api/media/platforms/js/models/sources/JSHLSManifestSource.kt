package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrNull
import com.futo.platformplayer.getOrThrow

class JSHLSManifestSource : IHLSManifestSource, JSSource {
    override val width : Int = 0;
    override val height : Int = 0;
    override val container : String get() = "application/vnd.apple.mpegurl";
    override val codec: String = "HLS";
    override val name : String;
    override val bitrate : Int? = null;
    override val url : String;
    override val duration: Long;

    override var priority: Boolean = false;

    override val language: String?;
    override val original: Boolean?;

    constructor(plugin: JSClient, obj: V8ValueObject) : super(TYPE_HLS, plugin, obj) {
        val contextName = "HLSSource";
        val config = plugin.config;

        name = _obj.getOrThrow(config, "name", contextName);
        url = _obj.getOrThrow(config, "url", contextName);
        duration = _obj.getOrThrow<Int>(config, "duration", contextName).toLong();

        priority = obj.getOrNull(config, "priority", contextName) ?: false;

        language = _obj.getOrNull(config, "language", contextName);
        original = _obj.getOrNull(config, "original", contextName);
    }
}