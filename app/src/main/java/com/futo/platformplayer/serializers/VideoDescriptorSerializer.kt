package com.futo.platformplayer.serializers

import com.futo.platformplayer.api.media.models.video.ISerializedVideoSourceDescriptor
import com.futo.platformplayer.api.media.models.video.SerializedVideoMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.video.SerializedVideoNonMuxedSourceDescriptor
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


class VideoDescriptorSerializer() : JsonContentPolymorphicSerializer<ISerializedVideoSourceDescriptor>(ISerializedVideoSourceDescriptor::class) {

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ISerializedVideoSourceDescriptor> {
        return when(element.jsonObject["isUnMuxed"]?.jsonPrimitive?.boolean) {
            false -> SerializedVideoMuxedSourceDescriptor.serializer();
            true -> SerializedVideoNonMuxedSourceDescriptor.serializer();
            else -> throw NotImplementedError("Unknown mux")
        };
    }
}