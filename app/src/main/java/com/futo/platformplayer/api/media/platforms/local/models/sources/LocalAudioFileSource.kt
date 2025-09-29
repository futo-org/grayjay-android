package com.futo.platformplayer.api.media.platforms.local.models.sources

import android.content.Context
import android.provider.MediaStore
import android.provider.MediaStore.Video
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.VideoUrlSource
import com.futo.platformplayer.helpers.VideoHelper
import com.futo.platformplayer.others.Language
import java.io.File

class LocalAudioFileSource: IAudioSource {


    override val name: String;
    override val container: String;
    override val codec: String = ""
    override val bitrate: Int = 0
    override val duration: Long;
    override val priority: Boolean = false;
    override val language: String = Language.UNKNOWN;
    override val original: Boolean = false;

    var file: File;

    constructor(file: File) {
        this.file = file;
        name = file.name;
        container = VideoHelper.videoExtensionToMimetype(file.extension) ?: "";
        duration = 0;
    }

}