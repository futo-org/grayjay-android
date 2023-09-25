package com.futo.platformplayer.api.media.models.streams

import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource

@kotlinx.serialization.Serializable
abstract class VideoUnMuxedSourceDescriptor : IVideoSourceDescriptor {
    override val isUnMuxed : Boolean = true;

    abstract override val videoSources : Array<IVideoSource>;
    abstract val audioSources : Array<IAudioSource>;
}