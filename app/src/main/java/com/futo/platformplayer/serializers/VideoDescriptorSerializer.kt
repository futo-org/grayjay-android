package com.futo.platformplayer.serializers

import com.futo.platformplayer.api.media.models.video.ISerializedVideoSourceDescriptor
import com.futo.platformplayer.api.media.models.video.SerializedVideoMuxedSourceDescriptor
import com.futo.platformplayer.api.media.models.video.SerializedVideoNonMuxedSourceDescriptor
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.*


class VideoDescriptorSerializer() : JsonContentPolymorphicSerializer<ISerializedVideoSourceDescriptor>(ISerializedVideoSourceDescriptor::class) {

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out ISerializedVideoSourceDescriptor> {
        return when(element.jsonObject["isUnMuxed"]?.jsonPrimitive?.boolean) {
            false -> SerializedVideoMuxedSourceDescriptor.serializer();
            true -> SerializedVideoNonMuxedSourceDescriptor.serializer();
            else -> throw NotImplementedError("Unknown mux")
        };
    }
}