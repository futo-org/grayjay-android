package com.futo.platformplayer.api.media.models.video

import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink

class PlatformVideoPlaceholder(url: String, val resumeSeconds: Long = 0) : IPlatformVideo {
    private var video: IPlatformVideo = SerializedPlatformVideo(
        id = PlatformID("Unknown", url),
        name = url,
        author = PlatformAuthorLink.UNKNOWN,
        url = url,
        shareUrl = url,
        duration = 0,
        viewCount = 0
    )

    override val contentType get() = video.contentType
    override val id get() = video.id
    override val name get() = video.name
    override val url get() = video.url
    override val shareUrl get() = video.shareUrl
    override val datetime get() = video.datetime
    override val author get() = video.author
    override val thumbnails get() = video.thumbnails
    override val duration get() = video.duration
    override val viewCount get() = video.viewCount
    override val playbackTime get() = video.playbackTime
    override val playbackDate get() = video.playbackDate
    override val isLive get() = video.isLive
    override val isShort get() = video.isShort


    val isResolved: Boolean get() = video is IPlatformVideoDetails


    fun resolve(details: IPlatformVideoDetails) {
        video = details
    }
}