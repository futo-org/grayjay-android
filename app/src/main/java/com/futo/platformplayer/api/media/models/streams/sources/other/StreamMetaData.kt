package com.futo.platformplayer.api.media.models.streams.sources.other

@kotlinx.serialization.Serializable
data class StreamMetaData(
    var fileInitStart: Int? = null,
    var fileInitEnd: Int? = null,
    var fileIndexStart: Int? = null,
    var fileIndexEnd: Int? = null
) {}