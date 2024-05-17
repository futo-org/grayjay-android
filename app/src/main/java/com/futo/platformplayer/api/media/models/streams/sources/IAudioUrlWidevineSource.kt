package com.futo.platformplayer.api.media.models.streams.sources

interface IAudioUrlWidevineSource : IAudioUrlSource {
    val bearerToken: String
    val licenseUri: String
}
