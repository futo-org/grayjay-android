package com.futo.platformplayer.api.media.models.post

import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.models.streams.IVideoSourceDescriptor
import com.futo.platformplayer.api.media.models.streams.sources.*
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.api.media.models.video.IPlatformVideo

/**
 * A detailed video model with data including video/audio sources
 */
interface IPlatformPostDetails : IPlatformPost, IPlatformContentDetails {
    val rating : IRating;

    val textType: TextType;
    val content: String;
}