package com.futo.platformplayer.api.media.platforms.local.models.sources

import android.content.Context
import android.provider.MediaStore
import android.provider.MediaStore.Video
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.VideoUrlSource
import com.futo.platformplayer.helpers.VideoHelper
import java.io.File

class LocalVideoFileSource: IVideoSource {


    override val name: String;
    override val width: Int;
    override val height: Int;
    override val container: String;
    override val codec: String = ""
    override val bitrate: Int = 0
    override val duration: Long;
    override val priority: Boolean = false;

    constructor(file: File) {
        name = file.name;
        width = 0;
        height = 0;
        container = VideoHelper.videoExtensionToMimetype(file.extension) ?: "";
        duration = 0;
    }

}