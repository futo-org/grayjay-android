package com.futo.platformplayer.api.media.models.locked

import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent

interface IPlatformLockedContent: IPlatformContent {
    val lockContentType: ContentType;
    val lockDescription: String?;
    val unlockUrl: String?;
    val contentName: String?;
    val contentThumbnails: Thumbnails;
}