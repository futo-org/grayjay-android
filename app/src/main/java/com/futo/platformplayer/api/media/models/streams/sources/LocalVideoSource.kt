package com.futo.platformplayer.api.media.models.streams.sources

import com.futo.platformplayer.api.media.models.streams.sources.other.IStreamMetaDataSource
import com.futo.platformplayer.api.media.models.streams.sources.other.StreamMetaData

@kotlinx.serialization.Serializable
class LocalVideoSource : IVideoSource, IStreamMetaDataSource {

    override val width : Int;
    override val height : Int;
    override val container : String;
    override val codec : String;
    override val name : String;
    override val bitrate : Int;
    override val duration : Long;

    override var priority: Boolean = false;

    val filePath : String;
    val fileSize : Long;

    //Only for particular videos
    override var streamMetaData: StreamMetaData? = null;

    constructor(name : String, filePath : String, fileSize: Long, width : Int = 0, height : Int = 0, duration: Long = 0, container : String = "", codec : String = "", bitrate : Int = 0) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.container = container;
        this.codec = codec;
        this.duration = duration;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.bitrate = bitrate;
    }

    companion object {
        fun fromSource(source: IVideoSource, path: String, fileSize: Long): LocalVideoSource {
            return LocalVideoSource(
                source.name,
                path,
                fileSize,
                source.width,
                source.height,
                source.duration,
                source.container,
                source.codec,
                source.bitrate?:0
            );
        }
    }
}