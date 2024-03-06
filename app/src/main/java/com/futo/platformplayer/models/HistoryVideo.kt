package com.futo.platformplayer.models

import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.serializers.OffsetDateTimeSerializer
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

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


    fun toReconString(): String {
        return "${video.url}|||${date.toEpochSecond()}|||${position}|||${video.name}";
    }

    companion object {
        fun fromReconString(str: String, resolve: ((url: String)->SerializedPlatformVideo?)? = null): HistoryVideo {
            var index = str.indexOf("|||");
            if(index < 0) throw IllegalArgumentException("Invalid history string: " + str);
            val url = str.substring(0, index);

            var indexNext = str.indexOf("|||", index + 3);
            if(indexNext < 0) throw IllegalArgumentException("Invalid history string: " + str);
            val dateSec = str.substring(index + 3, indexNext).toLong();

            index = indexNext;
            indexNext =  str.indexOf("|||", index + 3);
            if(indexNext < 0) throw IllegalArgumentException("Invalid history string: " + str);
            val position = str.substring(index + 3, indexNext).toLong();
            val name = str.substring(indexNext + 3);

            val video = resolve?.invoke(url) ?: SerializedPlatformVideo(
                id = PlatformID.asUrlID(url),
                name = name,
                thumbnails = Thumbnails(),
                author = PlatformAuthorLink(PlatformID.NONE, "Unknown", ""),
                datetime = null,
                url = url,
                shareUrl = url,
                duration = 0,
                viewCount = -1
            );

            return HistoryVideo(video, position, OffsetDateTime.of(LocalDateTime.ofEpochSecond(dateSec, 0, ZoneOffset.UTC), ZoneOffset.UTC));
        }
    }
}