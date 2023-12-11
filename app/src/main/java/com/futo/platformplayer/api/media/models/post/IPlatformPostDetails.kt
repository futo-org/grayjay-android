package com.futo.platformplayer.api.media.models.post

import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.ratings.IRating

/**
 * A detailed video model with data including video/audio sources
 */
interface IPlatformPostDetails : IPlatformPost, IPlatformContentDetails {
    val rating : IRating;

    val textType: TextType;
    val content: String;
}