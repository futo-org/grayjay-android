package com.futo.platformplayer.exceptions

import com.futo.platformplayer.api.media.models.channels.IPlatformChannel

class ChannelException : Exception {
    val url: String?;
    val channel: IPlatformChannel?;

    val channelNameOrUrl: String? get() = channel?.name ?: url;

    constructor(url: String, ex: Throwable): super("Channel: ${url} failed", ex) {
        this.url = url;
        this.channel = null;
    }
    constructor(channel: IPlatformChannel, ex: Throwable): super("Channel: ${channel.name} failed (${ex.message})", ex) {
        this.url = channel.url;
        this.channel = channel;
    }
}