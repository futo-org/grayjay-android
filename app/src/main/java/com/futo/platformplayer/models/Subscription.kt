package com.futo.platformplayer.models

import com.futo.platformplayer.api.media.models.ResultCapabilities
import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.channels.SerializedChannel
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.getNowDiffDays
import com.futo.platformplayer.serializers.OffsetDateTimeSerializer
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StateSubscriptions
import java.time.OffsetDateTime

@kotlinx.serialization.Serializable
class Subscription {
    var channel: SerializedChannel;

    //Settings
    var doNotifications: Boolean = false;
    var doFetchLive: Boolean = false;
    var doFetchStreams: Boolean = true;
    var doFetchVideos: Boolean = true;
    var doFetchPosts: Boolean = false;

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
    var uploadStreamInterval : Int = 0;
    var uploadPostInterval : Int = 0;

    var playbackSeconds: Int = 0;
    var playbackViews: Int = 0;


    constructor(channel : SerializedChannel) {
        this.channel = channel;
    }

    fun shouldFetchVideos() = doFetchVideos &&
            (lastVideo.getNowDiffDays() < 30 || lastVideoUpdate.getNowDiffDays() >= 1) &&
            (lastVideo.getNowDiffDays() < 180 || lastVideoUpdate.getNowDiffDays() >= 3);
    fun shouldFetchStreams() = doFetchStreams && lastLiveStream.getNowDiffDays() < 7;
    fun shouldFetchLiveStreams() = doFetchLive && lastLiveStream.getNowDiffDays() < 14;
    fun shouldFetchPosts() = doFetchPosts && lastPost.getNowDiffDays() < 5;

    fun getClient() = StatePlatform.instance.getChannelClientOrNull(channel.url);

    fun save() {
        StateSubscriptions.instance.saveSubscription(this);
    }
    fun saveAsync() {
        StateSubscriptions.instance.saveSubscription(this);
    }

    fun updateChannel(channel: IPlatformChannel) {
        this.channel = SerializedChannel.fromChannel(channel);
    }

    fun updatePlayback(content: IPlatformContentDetails, seconds: Int) {
        playbackSeconds += seconds;
    }
    fun addPlaybackView() {
        playbackViews += 1;
    }

    fun updateSubscriptionState(type: String, initialPage: List<IPlatformContent>) {
        val interval: Int;
        val mostRecent: OffsetDateTime?;
        if(!initialPage.isEmpty()) {
            val newestVideoDays = initialPage[0].datetime?.getNowDiffDays()?.toInt() ?: 0;
            val diffs = mutableListOf<Int>()
            for(i in (initialPage.size - 1) downTo  1) {
                val currentVideoDays = initialPage[i].datetime?.getNowDiffDays();
                val nextVideoDays = initialPage[i - 1].datetime?.getNowDiffDays();

                if(currentVideoDays != null && nextVideoDays != null) {
                    val diff = nextVideoDays - currentVideoDays;
                    diffs.add(diff.toInt());
                }
            }
            val averageDiff = if(diffs.size > 0)
                newestVideoDays.coerceAtLeast(diffs.average().toInt())
            else
                newestVideoDays;
            interval = averageDiff.coerceAtLeast(1);
            mostRecent = initialPage[0].datetime;
        }
        else {
            interval = 5;
            mostRecent = null;
        }
        when(type) {
            ResultCapabilities.TYPE_VIDEOS -> {
                uploadInterval = interval;
                if(mostRecent != null)
                    lastVideo = mostRecent;
                lastVideoUpdate = OffsetDateTime.now();
            }
            ResultCapabilities.TYPE_MIXED -> {
                uploadInterval = interval;
                if(mostRecent != null)
                    lastVideo = mostRecent;
                lastVideoUpdate = OffsetDateTime.now();
            }
            ResultCapabilities.TYPE_STREAMS -> {
                uploadStreamInterval = interval;
                if(mostRecent != null)
                    lastLiveStream = mostRecent;
                lastStreamUpdate = OffsetDateTime.now();
            }
            ResultCapabilities.TYPE_POSTS -> {
                uploadPostInterval = interval;
                if(mostRecent != null)
                    lastPost = mostRecent;
                lastPostUpdate = OffsetDateTime.now();
            }
        }
    }
}