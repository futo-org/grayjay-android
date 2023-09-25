package com.futo.platformplayer.api.media.models.live

interface ILiveEventChatMessage: IPlatformLiveEvent {

    val name: String;
    val thumbnail: String?;
    val message: String;
}