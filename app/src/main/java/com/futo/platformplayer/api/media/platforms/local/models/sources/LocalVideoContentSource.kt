package com.futo.platformplayer.api.media.platforms.local.models.sources

import android.content.Context
import android.provider.MediaStore
import android.provider.MediaStore.Video
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.VideoUrlSource
import com.futo.platformplayer.helpers.VideoHelper
import java.io.File

class LocalVideoContentSource: IVideoSource {


    override val name: String;
    override val width: Int;
    override val height: Int;
    override val container: String;
    override val codec: String = ""
    override val bitrate: Int = 0
    override val duration: Long;
    override val priority: Boolean = false;

    var contentUrl: String;

    constructor(contentUrl: String, mime: String, name: String? = null) {
        this.name = name ?: "File";
        width = 0;
        height = 0;
        container = mime;
        duration = 0;
        this.contentUrl = contentUrl;
    }
}