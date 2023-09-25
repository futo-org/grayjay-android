package com.futo.platformplayer.api.media.models.nested

import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent

interface IPlatformNestedContent: IPlatformContent {
    val nestedContentType: ContentType;
    val contentUrl: String;
    val contentName: String?;
    val contentDescription: String?;
    val contentProvider: String?
    val contentThumbnails: Thumbnails;
    val contentPlugin: String?;
    val contentSupported: Boolean;
}