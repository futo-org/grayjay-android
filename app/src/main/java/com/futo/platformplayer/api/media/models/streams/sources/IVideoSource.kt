package com.futo.platformplayer.api.media.models.streams.sources

interface IVideoSource {
    val name : String;
    val width : Int;
    val height : Int;
    val container : String;
    val codec : String;
    val bitrate : Int?;
    val duration: Long;
    val priority: Boolean;
    val language: String?;
    val original: Boolean?;
}