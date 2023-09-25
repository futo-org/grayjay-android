package com.futo.platformplayer.api.media.models.streams.sources

interface IAudioUrlSource : IAudioSource {
    fun getAudioUrl(): String;
}