package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.other.IStreamMetaDataSource
import com.futo.platformplayer.api.media.models.streams.sources.other.StreamMetaData
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrDefault

class JSAudioUrlRangeSource : JSAudioUrlSource, IStreamMetaDataSource {
    val hasItag: Boolean get() = itagId != null && initStart != null && initEnd != null && indexStart != null && indexEnd != null;
    val itagId: Int?;
    val initStart: Int?;
    val initEnd: Int?;

    val indexStart: Int?;
    val indexEnd: Int?;
    val audioChannels: Int;

    override val streamMetaData get() = if(initStart != null
        && initEnd != null
        && indexStart != null
        && indexEnd != null)
        StreamMetaData(initStart, initEnd, indexStart, indexEnd) else null;

    constructor(plugin: JSClient, obj: V8ValueObject) : super(plugin, obj) {
        val contextName = "JSAudioUrlRangeSource";
        val config = plugin.config;

        itagId = _obj.getOrDefault(config, "itagId", contextName, null);
        initStart = _obj.getOrDefault(config, "initStart", contextName, null);
        initEnd = _obj.getOrDefault(config, "initEnd", contextName, null);
        indexStart = _obj.getOrDefault(config, "indexStart", contextName, null);
        indexEnd = _obj.getOrDefault(config, "indexEnd", contextName, null);
        audioChannels = _obj.getOrDefault(config, "audioChannels", contextName, 2) ?: 2;
    }

    override fun toString(): String {
        return "RangeSource(url=[${getAudioUrl()}], itagId=[${itagId}], initStart=[${initStart}], initEnd=[${initEnd}], indexStart=[${indexStart}], indexEnd=[${indexEnd}]))";
        return super.toString()
    }
}