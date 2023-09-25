package com.futo.platformplayer.api.media.models.streams.sources

class HLSManifestSource : IVideoSource, IHLSManifestSource {
    override val width : Int = 0;
    override val height : Int = 0;
    override val container : String = "HLS";
    override val codec: String = "HLS";
    override val name : String = "HLS";
    override val bitrate : Int? = null;
    override val url : String;
    override val duration: Long = 0;

    override var priority: Boolean = false;

    constructor(url : String) {
        this.url = url;
    }
}