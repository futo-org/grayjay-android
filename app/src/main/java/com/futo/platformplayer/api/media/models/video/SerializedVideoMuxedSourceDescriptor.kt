package com.futo.platformplayer.api.media.models.video

import com.futo.platformplayer.api.media.models.streams.VideoMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.VideoUrlSource

@kotlinx.serialization.Serializable
class SerializedVideoMuxedSourceDescriptor(
    val _videoSources: Array<VideoUrlSource>
): VideoMuxedSourceDescriptor(), ISerializedVideoSourceDescriptor {
    @kotlinx.serialization.Transient
    override val videoSources: Array<IVideoSource> get() = _videoSources.map { it }.toTypedArray();
};