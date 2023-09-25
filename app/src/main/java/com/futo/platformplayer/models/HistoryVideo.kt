package com.futo.platformplayer.models

import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.serializers.OffsetDateTimeSerializer
import java.time.OffsetDateTime

@kotlinx.serialization.Serializable
class HistoryVideo {
    var video: SerializedPlatformVideo;
    var position: Long;

    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var date: OffsetDateTime;


    constructor(video: SerializedPlatformVideo, position: Long, date: OffsetDateTime) {
        this.video = video;
        this.position = position;
        this.date = date;
    }
}