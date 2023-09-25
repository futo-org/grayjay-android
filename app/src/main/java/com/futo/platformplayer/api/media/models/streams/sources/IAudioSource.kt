package com.futo.platformplayer.api.media.models.streams.sources

interface IAudioSource {
    val name : String;
    val bitrate : Int;
    val container : String;
    val codec : String;
    val language : String;
    val duration : Long?;
    val priority: Boolean;
}