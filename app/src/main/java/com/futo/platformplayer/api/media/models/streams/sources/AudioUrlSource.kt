package com.futo.platformplayer.api.media.models.streams.sources

import com.futo.platformplayer.api.media.models.streams.sources.other.IStreamMetaDataSource
import com.futo.platformplayer.api.media.models.streams.sources.other.StreamMetaData
import com.futo.platformplayer.others.Language

@kotlinx.serialization.Serializable
class AudioUrlSource(
    override val name: String,
    val url : String,
    override val bitrate : Int,
    override val container : String = "",
    override val codec: String = "",
    override val language: String = Language.UNKNOWN,
    override val duration: Long? = null,
    override var priority: Boolean = false
) : IAudioUrlSource, IStreamMetaDataSource{
    override var streamMetaData: StreamMetaData? = null;

    override fun getAudioUrl() : String {
        return url;
    }

    companion object {
        fun fromUrlSource(source: IAudioUrlSource?): AudioUrlSource? {
            if(source == null)
                return null;

            val streamData = if(source is IStreamMetaDataSource)
                source.streamMetaData else null;

            val ret = AudioUrlSource(
                source.name,
                source.getAudioUrl(),
                source.bitrate,
                source.container,
                source.codec,
                source.language,
                source.duration
            );
            ret.streamMetaData = streamData;

            return ret;
        }
    }
}