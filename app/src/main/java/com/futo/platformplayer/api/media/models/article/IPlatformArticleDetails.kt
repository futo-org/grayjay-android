package com.futo.platformplayer.api.media.models.article

import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.platforms.js.models.IJSArticleSegment

interface IPlatformArticleDetails: IPlatformContent, IPlatformArticle, IPlatformContentDetails {
    val segments: List<IJSArticleSegment>;
    val rating : IRating;
}