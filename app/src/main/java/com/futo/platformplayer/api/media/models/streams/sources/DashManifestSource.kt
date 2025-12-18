package com.futo.platformplayer.api.media.models.streams.sources

class DashManifestSource : IVideoSource, IDashManifestSource {
    override val width : Int = 0;
    override val height : Int = 0;
    override val container : String = "Dash";
    override val codec: String = "Dash";
    override val name : String = "Dash";
    override val bitrate: Int? = null;
    override val url : String;
    override val duration: Long get() = 0;

    override var priority: Boolean = false;

    override val language: String? = null;
    override val original: Boolean? = false;

    constructor(url : String) {
        this.url = url;
    }
}