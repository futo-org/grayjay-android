package com.futo.platformplayer.api.media.models.streams

import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.platforms.local.models.sources.LocalAudioContentSource
import com.futo.platformplayer.downloads.VideoLocal


class LocalVideoUnMuxedSourceDescriptor : VideoUnMuxedSourceDescriptor {
    override val videoSources: Array<IVideoSource>;
    override val audioSources: Array<IAudioSource>;

    constructor(video: VideoLocal) {
        videoSources = video.videoSource.toTypedArray();
        audioSources = video.audioSource.toTypedArray();
    }
    constructor(audio: LocalAudioContentSource) {
        videoSources = arrayOf()
        audioSources = arrayOf(audio);
    }
    constructor(videoSources: Array<IVideoSource>, audioSources: Array<IAudioSource>) {
        this.videoSources = videoSources;
        this.audioSources = audioSources;
    }
}