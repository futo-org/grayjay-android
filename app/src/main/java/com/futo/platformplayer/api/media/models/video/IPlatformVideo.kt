package com.futo.platformplayer.api.media.models.video

import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import java.time.OffsetDateTime

/**
 * A search result representing a video (overview data)
 */
interface IPlatformVideo : IPlatformContent {
    val thumbnails: Thumbnails;

    val duration: Long;
    val viewCount: Long;

    val playbackTime: Long;
    val playbackDate: OffsetDateTime?;

    val isLive : Boolean;

    val isShort: Boolean;
}