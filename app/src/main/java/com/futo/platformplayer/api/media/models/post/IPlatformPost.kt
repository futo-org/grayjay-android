package com.futo.platformplayer.api.media.models.post

import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.contents.IPlatformContent

/**
 * A search result representing a video (overview data)
 */
interface IPlatformPost: IPlatformContent {
    val description: String;
    val thumbnails: List<Thumbnails?>;
    val images: List<String>;
}