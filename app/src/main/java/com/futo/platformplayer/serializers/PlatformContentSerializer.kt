package com.futo.platformplayer.serializers

import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.models.video.SerializedPlatformContent
import com.futo.platformplayer.api.media.models.video.SerializedPlatformNestedContent
import com.futo.platformplayer.api.media.models.video.SerializedPlatformPost
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.*


class PlatformContentSerializer() : JsonContentPolymorphicSerializer<SerializedPlatformContent>(SerializedPlatformContent::class) {

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out SerializedPlatformContent> {
        val obj = element.jsonObject["contentType"];

        //TODO: Remove this temporary fallback..at some point
        if(obj == null && element.jsonObject["isLive"]?.jsonPrimitive?.booleanOrNull != null)
            return SerializedPlatformVideo.serializer();

        if(obj?.jsonPrimitive?.isString ?: true)
            return when(obj?.jsonPrimitive?.contentOrNull) {
                "MEDIA" -> SerializedPlatformVideo.serializer();
                "NESTED_VIDEO" -> SerializedPlatformNestedContent.serializer();
                "ARTICLE" -> throw NotImplementedError("Articles not yet implemented");
                "POST" -> SerializedPlatformPost.serializer();
                else -> throw NotImplementedError("Unknown Content Type Value: ${obj?.jsonPrimitive?.contentOrNull}")
            };
        else
            return when(obj?.jsonPrimitive?.int) {
                ContentType.MEDIA.value -> SerializedPlatformVideo.serializer();
                ContentType.NESTED_VIDEO.value -> SerializedPlatformNestedContent.serializer();
                ContentType.ARTICLE.value -> throw NotImplementedError("Articles not yet implemented");
                ContentType.POST.value -> SerializedPlatformPost.serializer();
                else -> throw NotImplementedError("Unknown Content Type Value: ${obj?.jsonPrimitive?.int}")
            };
    }
}