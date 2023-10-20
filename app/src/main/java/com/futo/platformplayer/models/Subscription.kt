package com.futo.platformplayer.models

import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.channels.SerializedChannel
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.getNowDiffDays
import com.futo.platformplayer.serializers.OffsetDateTimeSerializer
import com.futo.platformplayer.states.StatePlatform
import java.time.OffsetDateTime

@kotlinx.serialization.Serializable
class Subscription {
    var channel: SerializedChannel;

    //Last found content
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastVideo : OffsetDateTime = OffsetDateTime.MAX;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastLiveStream : OffsetDateTime = OffsetDateTime.MAX;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastPost : OffsetDateTime = OffsetDateTime.MAX;

    //Last update time
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastVideoUpdate : OffsetDateTime = OffsetDateTime.MIN;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastStreamUpdate : OffsetDateTime = OffsetDateTime.MIN;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastLiveStreamUpdate : OffsetDateTime = OffsetDateTime.MIN;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastPostUpdate : OffsetDateTime = OffsetDateTime.MIN;

    //Last video interval
    var uploadInterval : Int = 0;
    var uploadPostInterval : Int = 0;


    constructor(channel : SerializedChannel) {
        this.channel = channel;
    }

    fun shouldFetchStreams() = lastLiveStream.getNowDiffDays() < 7;
    fun shouldFetchLiveStreams() = lastLiveStream.getNowDiffDays() < 14;
    fun shouldFetchPosts() = lastPost.getNowDiffDays() < 2;

    fun getClient() = StatePlatform.instance.getChannelClientOrNull(channel.url);

    fun updateChannel(channel: IPlatformChannel) {
        this.channel = SerializedChannel.fromChannel(channel);
    }

    fun updateVideoStatus(allVideos: List<IPlatformContent>? = null, liveStreams: List<IPlatformContent>? = null) {

    }
}