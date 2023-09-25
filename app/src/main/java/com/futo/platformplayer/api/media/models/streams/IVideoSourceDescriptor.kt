package com.futo.platformplayer.api.media.models.streams

import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource

interface IVideoSourceDescriptor {
    val isUnMuxed: Boolean;
    val videoSources: Array<IVideoSource>;
}