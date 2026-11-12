package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestAudioSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.ensureIsBusy
import com.futo.platformplayer.getOrNull
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.orNull

class JSHLSManifestAudioSource : IHLSManifestAudioSource, JSSource {
    override val container : String get() = "application/vnd.apple.mpegurl";
    override val codec: String = "HLS";
    override val name : String;
    override val bitrate : Int = 0;
    override val url : String;
    override val duration: Long;
    override val language: String;

    override var priority: Boolean = false;
    override var original: Boolean = false;

    constructor(plugin: JSClient, obj: V8ValueObject) : super(TYPE_HLS, plugin, obj) {
        val contextName = "HLSAudioSource";
        val config = plugin.config;

        name = _obj.getOrThrow(config, "name", contextName);
        url = _obj.getOrThrow(config, "url", contextName);
        duration = _obj.getOrThrow<Int>(config, "duration", contextName).toLong();
        language = _obj.getOrThrow(config, "language", contextName);

        priority = obj.getOrNull(config, "priority", contextName) ?: false;
        original =  if(_obj.has("original")) obj.getOrThrow(config, "original", contextName) else false;
    }


    companion object {
        fun fromV8HLSNullable(plugin: JSClient, obj: V8Value?) : JSHLSManifestAudioSource? {
            obj?.ensureIsBusy();
            return obj.orNull { fromV8HLS(plugin, it as V8ValueObject) }
        };
        fun fromV8HLS(plugin: JSClient, obj: V8ValueObject) : JSHLSManifestAudioSource {
            obj.ensureIsBusy();
            return JSHLSManifestAudioSource(plugin, obj)
        };
    }
}