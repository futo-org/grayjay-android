package com.futo.platformplayer.api.media.models.video

import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.nested.IPlatformNestedContent
import com.futo.platformplayer.api.media.models.post.IPlatformPost
import com.futo.platformplayer.serializers.PlatformContentSerializer

@kotlinx.serialization.Serializable(with = PlatformContentSerializer::class)
interface SerializedPlatformContent: IPlatformContent {
    fun toJson() : String;
    fun fromJson(str : String) : SerializedPlatformContent;
    fun fromJsonArray(str : String) : Array<SerializedPlatformContent>;

    companion object {
        fun fromContent(content : IPlatformContent) : SerializedPlatformContent {
            return when(content.contentType) {
                ContentType.MEDIA -> SerializedPlatformVideo.fromVideo(content as IPlatformVideo);
                ContentType.NESTED_VIDEO -> SerializedPlatformNestedContent.fromNested(content as IPlatformNestedContent);
                ContentType.POST -> SerializedPlatformPost.fromPost(content as IPlatformPost);
                else -> throw NotImplementedError("Content type ${content.contentType} not implemented");
            };
        }
    }
}