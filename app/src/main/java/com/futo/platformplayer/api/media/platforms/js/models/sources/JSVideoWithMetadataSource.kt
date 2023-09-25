package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.other.IStreamMetaDataSource
import com.futo.platformplayer.api.media.models.streams.sources.other.StreamMetaData
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.getOrDefault

class JSVideoUrlRangeSource : JSVideoUrlSource, IStreamMetaDataSource {
    val hasItag: Boolean get() = itagId != null && initStart != null && initEnd != null && indexStart != null && indexEnd != null;
    val itagId: Int?;
    val initStart: Int?;
    val initEnd: Int?;

    val indexStart: Int?;
    val indexEnd: Int?;

    override val streamMetaData get() = if(initStart != null
        && initEnd != null
        && indexStart != null
        && indexEnd != null)
            StreamMetaData(initStart, initEnd, indexStart, indexEnd) else null;

    constructor(config: IV8PluginConfig, obj: V8ValueObject) : super(config, obj) {
        val contextName = "JSVideoUrlRangeSource";

        itagId = _obj.getOrDefault(config, "itagId", contextName, null);
        initStart = _obj.getOrDefault(config, "initStart", contextName, null);
        initEnd = _obj.getOrDefault(config, "initEnd", contextName, null);
        indexStart = _obj.getOrDefault(config, "indexStart", contextName, null);
        indexEnd = _obj.getOrDefault(config, "indexEnd", contextName, null);
    }
}