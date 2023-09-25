package com.futo.platformplayer.api.media.models

import com.caoccao.javet.values.reference.V8ValueArray
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.getOrThrow

@kotlinx.serialization.Serializable
class Thumbnails {
    val sources : Array<Thumbnail>;

    constructor() { sources = arrayOf(); }
    constructor(thumbnails : Array<Thumbnail>) {
        sources = thumbnails.filter {it.url != null} .sortedBy { it.quality }.toTypedArray();
    }

    fun getHQThumbnail() : String? {
        return sources.lastOrNull()?.url;
    }
    fun getLQThumbnail() : String? {
        return sources.firstOrNull()?.url;
    }
    fun hasMultiple() = sources.size > 1;


    companion object {
        fun fromV8(config: IV8PluginConfig, value: V8ValueObject): Thumbnails {
            return Thumbnails((value.getOrThrow<V8ValueArray>(config, "sources", "Thumbnails"))
                .toArray()
                .map { Thumbnail.fromV8(it as V8ValueObject) }
                .toTypedArray());
        }
    }
}
@kotlinx.serialization.Serializable
data class Thumbnail(val url : String?, val quality : Int = 0) {

    companion object {
        fun fromV8(value: V8ValueObject): Thumbnail {
            return Thumbnail(
                value.getString("url"),
                value.getInteger("quality"));
        }
    }
};