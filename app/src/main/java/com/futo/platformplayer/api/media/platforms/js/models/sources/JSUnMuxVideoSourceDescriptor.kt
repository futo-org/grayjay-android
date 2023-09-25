package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueArray
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.VideoUnMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.getOrThrow

class JSUnMuxVideoSourceDescriptor: VideoUnMuxedSourceDescriptor {
    protected val _obj: V8ValueObject;

    override val isUnMuxed: Boolean;
    override val videoSources: Array<IVideoSource>;
    override val audioSources: Array<IAudioSource>;

    constructor(config: IV8PluginConfig, obj: V8ValueObject) {
        this._obj = obj;
        val contextName = "UnMuxVideoSource"
        this.isUnMuxed = obj.getOrThrow(config, "isUnMuxed", contextName);
        this.videoSources = obj.getOrThrow<V8ValueArray>(config, "videoSources", contextName).toArray()
            .map { JSSource.fromV8Video(config, it as V8ValueObject) }
            .toTypedArray();
        this.audioSources = obj.getOrThrow<V8ValueArray>(config, "audioSources", contextName).toArray()
            .map { JSSource.fromV8Audio(config, it as V8ValueObject) }
            .toTypedArray();
    }
}