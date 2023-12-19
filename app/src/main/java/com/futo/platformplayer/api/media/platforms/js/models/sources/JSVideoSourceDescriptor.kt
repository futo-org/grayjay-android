package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueArray
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.IVideoSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.VideoMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.getOrThrow

class JSVideoSourceDescriptor : VideoMuxedSourceDescriptor {
    protected val _obj: V8ValueObject;

    override val isUnMuxed: Boolean;
    override val videoSources: Array<IVideoSource>;

    constructor(plugin: JSClient, obj: V8ValueObject) {
        this._obj = obj;
        val config = plugin.config;
        val contextName = "VideoSourceDescriptor";
        this.isUnMuxed = obj.getOrThrow(config, "isUnMuxed", contextName);
        this.videoSources = obj.getOrThrow<V8ValueArray>(config, "videoSources", contextName).toArray()
            .map { JSSource.fromV8Video(plugin, it as V8ValueObject) }
            .toTypedArray();
    }

    companion object {
        const val TYPE_MUXED = "MuxVideoSourceDescriptor";
        const val TYPE_UNMUXED = "UnMuxVideoSourceDescriptor";


        fun fromV8(plugin: JSClient, obj: V8ValueObject) : IVideoSourceDescriptor {
            val type = obj.getString("plugin_type")
            return when(type) {
                TYPE_MUXED -> JSVideoSourceDescriptor(plugin, obj);
                TYPE_UNMUXED -> JSUnMuxVideoSourceDescriptor(plugin, obj);
                else -> throw NotImplementedError("Unknown type: ${type}");
            }
        }
    }
}