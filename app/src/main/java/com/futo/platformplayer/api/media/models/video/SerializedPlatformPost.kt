package com.futo.platformplayer.api.media.models.video

import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.Serializer
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.Thumbnails
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.post.IPlatformPost
import com.futo.platformplayer.serializers.OffsetDateTimeNullableSerializer
import com.futo.polycentric.core.combineHashCodes
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime

@kotlinx.serialization.Serializable
open class SerializedPlatformPost(
    override val id: PlatformID,
    override val name: String,
    override val url: String,
    override val shareUrl: String,
    @kotlinx.serialization.Serializable(with = OffsetDateTimeNullableSerializer::class)
    override val datetime: OffsetDateTime?,
    override val author: PlatformAuthorLink,
    override val description: String,
    override val thumbnails: List<Thumbnails?>,
    override val images: List<String>
) : IPlatformPost, SerializedPlatformContent {
    override val contentType: ContentType = ContentType.POST;

    override fun toJson() : String {
        return Json.encodeToString(this);
    }
    override fun fromJson(str : String) : SerializedPlatformVideo {
        return Serializer.json.decodeFromString<SerializedPlatformVideo>(str);
    }
    override fun fromJsonArray(str : String) : Array<SerializedPlatformContent> {
        return Serializer.json.decodeFromString<Array<SerializedPlatformContent>>(str);
    }

    companion object {
        fun fromPost(post: IPlatformPost) : SerializedPlatformPost {
            return SerializedPlatformPost(
                post.id,
                post.name,
                post.url,
                post.shareUrl,
                post.datetime,
                post.author,
                post.description,
                post.thumbnails,
                post.images
            );
        }
    }
}