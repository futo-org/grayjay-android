package com.futo.platformplayer.api.media.models.video

import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.Serializer
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.nested.IPlatformNestedContent
import com.futo.platformplayer.serializers.OffsetDateTimeNullableSerializer
import com.futo.platformplayer.states.StatePlatform
import com.futo.polycentric.core.combineHashCodes
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime

@kotlinx.serialization.Serializable
open class SerializedPlatformNestedContent(
    override val id: PlatformID,
    override val name: String,
    override val author: PlatformAuthorLink,
    @kotlinx.serialization.Serializable(with = OffsetDateTimeNullableSerializer::class)
    override val datetime: OffsetDateTime?,
    override val url: String,
    override val shareUrl: String,
    override val nestedContentType: ContentType,
    override val contentUrl: String,
    override val contentName: String?,
    override val contentDescription: String?,
    override val contentProvider: String?,
    override val contentThumbnails: Thumbnails
) : IPlatformNestedContent, SerializedPlatformContent {
    final override val contentType: ContentType = ContentType.NESTED_VIDEO;

    override val contentPlugin: String? = StatePlatform.instance.getContentClientOrNull(contentUrl)?.id;
    override val contentSupported: Boolean get() = contentPlugin != null;

    override fun toJson() : String {
        return Json.encodeToString(this);
    }
    override fun fromJson(str : String) : SerializedPlatformNestedContent {
        return Serializer.json.decodeFromString<SerializedPlatformNestedContent>(str);
    }
    override fun fromJsonArray(str : String) : Array<SerializedPlatformContent> {
        return Serializer.json.decodeFromString<Array<SerializedPlatformContent>>(str);
    }

    companion object {
        fun fromNested(content: IPlatformNestedContent) : SerializedPlatformNestedContent {
            return SerializedPlatformNestedContent(
                content.id,
                content.name,
                content.author,
                content.datetime,
                content.url,
                content.shareUrl,
                content.nestedContentType,
                content.contentUrl,
                content.contentName,
                content.contentDescription,
                content.contentProvider,
                content.contentThumbnails
            );
        }
    }
}