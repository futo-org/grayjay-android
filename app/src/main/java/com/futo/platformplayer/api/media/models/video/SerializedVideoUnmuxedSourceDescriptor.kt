package com.futo.platformplayer.api.media.models.video

import com.futo.platformplayer.api.media.models.streams.VideoUnMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.AudioUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.VideoUrlSource

@kotlinx.serialization.Serializable
class SerializedVideoNonMuxedSourceDescriptor(
    val _videoSources: Array<VideoUrlSource>,
    val _audioSources: Array<AudioUrlSource>
): VideoUnMuxedSourceDescriptor(), ISerializedVideoSourceDescriptor {
    override val videoSources: Array<IVideoSource> get() = _videoSources.map { it }.toTypedArray();
    override val audioSources: Array<IAudioSource> get() = _audioSources.map { it }.toTypedArray();
};