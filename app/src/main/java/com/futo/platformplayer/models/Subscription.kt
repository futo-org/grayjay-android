package com.futo.platformplayer.models

import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.channels.SerializedChannel
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.serializers.OffsetDateTimeSerializer
import java.time.OffsetDateTime

@kotlinx.serialization.Serializable
class Subscription {
    var channel: SerializedChannel;

    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastVideo : OffsetDateTime = OffsetDateTime.MAX;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastLiveStream : OffsetDateTime = OffsetDateTime.MAX;

    var uploadInterval : Int = 0;

    constructor(channel : SerializedChannel) {
        this.channel = channel;
    }

    fun updateChannel(channel: IPlatformChannel) {
        this.channel = SerializedChannel.fromChannel(channel);
    }

    fun updateVideoStatus(allVideos: List<IPlatformContent>? = null, liveStreams: List<IPlatformContent>? = null) {

    }
}