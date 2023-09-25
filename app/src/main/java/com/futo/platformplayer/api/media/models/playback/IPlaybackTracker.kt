package com.futo.platformplayer.api.media.models.playback

interface IPlaybackTracker {
    val nextRequest: Int;

    fun shouldUpdate(): Boolean;

    fun onInit(seconds: Double);
    fun onProgress(seconds: Double, isPlaying: Boolean);
}