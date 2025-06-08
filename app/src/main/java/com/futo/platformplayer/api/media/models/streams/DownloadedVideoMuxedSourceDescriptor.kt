package com.futo.platformplayer.api.media.models.streams

import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.downloads.VideoLocal

class DownloadedVideoMuxedSourceDescriptor(
        private val video: VideoLocal
    ) : VideoMuxedSourceDescriptor() {
    override val videoSources: Array<IVideoSource> get() = video.videoSource.toTypedArray();
}