package com.futo.platformplayer.api.media.models.streams.sources

interface IHLSManifestSource : IVideoSource {
    val url : String;
}
interface IHLSManifestAudioSource : IAudioSource {
    val url : String;
}