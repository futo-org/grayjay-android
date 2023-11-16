package com.futo.platformplayer.api.media.models.video

import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.Serializer
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.locked.IPlatformLockedContent
import com.futo.platformplayer.api.media.models.nested.IPlatformNestedContent
import com.futo.platformplayer.serializers.OffsetDateTimeNullableSerializer
import com.futo.platformplayer.states.StatePlatform
import com.futo.polycentric.core.combineHashCodes
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime

@kotlinx.serialization.Serializable
open class SerializedPlatformLockedContent(
    override val id: PlatformID,
    override val name: String,
    override val author: PlatformAuthorLink,
    @kotlinx.serialization.Serializable(with = OffsetDateTimeNullableSerializer::class)
    override val datetime: OffsetDateTime?,
    override val url: String,
    override val shareUrl: String,
    override val lockContentType: ContentType,
    override val contentName: String?,
    override val lockDescription: String? = null,
    override val unlockUrl: String? = null,
    override val contentThumbnails: Thumbnails
) : IPlatformLockedContent, SerializedPlatformContent {
    final override val contentType: ContentType get() = ContentType.LOCKED;

    override fun toJson() : String {
        return Json.encodeToString(this);
    }
    override fun fromJson(str : String) : SerializedPlatformLockedContent {
        return Serializer.json.decodeFromString<SerializedPlatformLockedContent>(str);
    }
    override fun fromJsonArray(str : String) : Array<SerializedPlatformContent> {
        return Serializer.json.decodeFromString<Array<SerializedPlatformContent>>(str);
    }

    companion object {
        fun fromLocked(content: IPlatformLockedContent) : SerializedPlatformLockedContent {
            return SerializedPlatformLockedContent(
                content.id,
                content.name,
                content.author,
                content.datetime,
                content.url,
                content.shareUrl,
                content.lockContentType,
                content.contentName,
                content.lockDescription,
                content.unlockUrl,
                content.contentThumbnails
            );
        }
    }
}