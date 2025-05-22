package com.futo.platformplayer.api.media.platforms.local.models

import com.futo.platformplayer.api.media.models.streams.VideoMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.LocalVideoSource
import com.futo.platformplayer.api.media.platforms.local.models.sources.LocalVideoFileSource
import com.futo.platformplayer.downloads.VideoLocal

class LocalVideoMuxedSourceDescriptor(
    private val video: LocalVideoFileSource
) : VideoMuxedSourceDescriptor() {
    override val videoSources: Array<IVideoSource> get() = arrayOf(video);
}