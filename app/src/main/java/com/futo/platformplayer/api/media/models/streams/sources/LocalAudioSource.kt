package com.futo.platformplayer.api.media.models.streams.sources

import com.futo.platformplayer.api.media.models.streams.sources.other.IStreamMetaDataSource
import com.futo.platformplayer.api.media.models.streams.sources.other.StreamMetaData
import com.futo.platformplayer.others.Language

@kotlinx.serialization.Serializable
class LocalAudioSource : IAudioSource, IStreamMetaDataSource {

    override val name: String;
    override val bitrate : Int;
    override val container : String;
    override val codec : String;
    override val language: String;
    override val duration: Long? = null;

    override var priority: Boolean = false;
    override val original: Boolean = false;

    val filePath : String;
    val fileSize: Long;

    //Only for particular videos
    override var streamMetaData: StreamMetaData? = null;

    constructor(name: String?, filePath : String, fileSize: Long, bitrate : Int, container : String = "", codec: String = "", language: String = Language.UNKNOWN) {
        this.name = name ?: "${container} ${bitrate}";
        this.bitrate = bitrate;
        this.container = container;
        this.codec = codec;
        this.filePath = filePath;
        this.language = language;
        this.fileSize = fileSize;
    }

    companion object {
        fun fromSource(source: IAudioSource, path: String, fileSize: Long, overrideContainer: String? = null): LocalAudioSource {
            return LocalAudioSource(
                source.name,
                path,
                fileSize,
                source.bitrate,
                overrideContainer ?: source.container,
                source.codec,
                source.language
            );
        }
    }
}