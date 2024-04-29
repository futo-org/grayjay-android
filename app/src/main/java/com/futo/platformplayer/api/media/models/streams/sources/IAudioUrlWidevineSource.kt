package com.futo.platformplayer.api.media.models.streams.sources

interface IAudioUrlWidevineSource : IAudioUrlSource {
    fun getBearerToken(): String
    fun getLicenseUri(): String
}
