package com.futo.platformplayer.api.media.models.streams.sources

import com.futo.platformplayer.api.media.models.streams.sources.other.IStreamMetaDataSource
import com.futo.platformplayer.api.media.models.streams.sources.other.StreamMetaData

@kotlinx.serialization.Serializable
open class VideoUrlSource(
    override val name : String,
    val url : String,
    override val width : Int = 0,
    override val height : Int = 0,
    override val duration: Long = 0,
    override val container : String = "",
    override val codec : String = "",
    override val bitrate : Int? = 0,

    override var priority: Boolean = false
) : IVideoUrlSource, IStreamMetaDataSource {
    override var streamMetaData: StreamMetaData? = null;

    override fun getVideoUrl() : String {
        return url;
    }

    companion object {
        fun fromUrlSource(source: IVideoUrlSource?): VideoUrlSource? {
            if(source == null)
                return null;

            val streamData = if(source is IStreamMetaDataSource)
                source.streamMetaData else null;

            val ret = VideoUrlSource(
                source.name,
                source.getVideoUrl(),
                source.width,
                source.height,
                source.duration,
                source.container,
                source.codec,
                source.bitrate
            );
            ret.streamMetaData = streamData;

            return ret;
        }
    }
}