package com.futo.platformplayer.api.media.models.article

import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.contents.IPlatformContent

interface IPlatformArticle: IPlatformContent {
    val summary: String?;
    val thumbnails: Thumbnails?;
}