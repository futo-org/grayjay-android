package com.futo.platformplayer.api.media.models.contents

import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.time.OffsetDateTime

interface IPlatformContent {
    val contentType: ContentType;

    val id: PlatformID;
    val name: String;
    val url: String;
    val shareUrl: String;

    val datetime: OffsetDateTime?;

    val author: PlatformAuthorLink;
}