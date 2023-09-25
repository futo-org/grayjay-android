package com.futo.platformplayer.api.media.models.streams.sources

interface IVideoUrlSource : IVideoSource {
    fun getVideoUrl(): String;
}