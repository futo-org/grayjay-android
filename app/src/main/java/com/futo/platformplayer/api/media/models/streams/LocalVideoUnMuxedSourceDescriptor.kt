package com.futo.platformplayer.api.media.models.streams

import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.downloads.VideoLocal


class LocalVideoUnMuxedSourceDescriptor(private val video: VideoLocal) : VideoUnMuxedSourceDescriptor() {
    override val videoSources: Array<IVideoSource> get() = video.videoSource.toTypedArray();
    override val audioSources: Array<IAudioSource> get() = video.audioSource.toTypedArray();
}