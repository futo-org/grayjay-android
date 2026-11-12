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

class LocalAudioContentSource : IAudioSource {

    override val name: String;
    override val container: String;
    override val codec: String = ""
    override val bitrate: Int = 0
    override val duration: Long;
    override val priority: Boolean = false;
    override val language: String = Language.UNKNOWN
    override val original: Boolean = false;

    var contentUrl: String;

    constructor(contentUrl: String, mime: String, name: String? = null) {
        this.name = name ?: "File";
        container = mime;
        duration = 0;

        this.contentUrl = contentUrl;
    }
}