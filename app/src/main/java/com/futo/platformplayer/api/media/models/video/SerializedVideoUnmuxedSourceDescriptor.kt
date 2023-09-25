package com.futo.platformplayer.api.media.models.video

import com.futo.platformplayer.api.media.models.streams.VideoUnMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.*

@kotlinx.serialization.Serializable
class SerializedVideoNonMuxedSourceDescriptor(
    val _videoSources: Array<VideoUrlSource>,
    val _audioSources: Array<AudioUrlSource>
): VideoUnMuxedSourceDescriptor(), ISerializedVideoSourceDescriptor {
    @kotlinx.serialization.Transient
    override val videoSources: Array<IVideoSource> get() = _videoSources.map { it }.toTypedArray();
    @kotlinx.serialization.Transient
    override val audioSources: Array<IAudioSource> get() = _audioSources.map { it }.toTypedArray();
};