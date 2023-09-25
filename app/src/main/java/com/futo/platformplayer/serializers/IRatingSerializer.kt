package com.futo.platformplayer.serializers

import com.futo.platformplayer.api.media.models.ratings.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.*
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.reflect.KClass


class IRatingSerializer() : JsonContentPolymorphicSerializer<IRating>(IRating::class) {

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out IRating> {
        val obj = element.jsonObject["type"];
        if(obj?.jsonPrimitive?.isString ?: true)
            return when(obj?.jsonPrimitive?.contentOrNull) {
                "LIKES" -> RatingLikes.serializer();
                "LIKEDISLIKES" -> RatingLikeDislikes.serializer();
                "SCALE" -> RatingScaler.serializer();
                else -> throw NotImplementedError("Rating Value: ${obj?.jsonPrimitive?.contentOrNull}")
            };
        else
            return when(element.jsonObject["type"]?.jsonPrimitive?.int) {
                RatingType.LIKES.value -> RatingLikes.serializer();
                RatingType.LIKEDISLIKES.value -> RatingLikeDislikes.serializer();
                RatingType.SCALE.value -> RatingScaler.serializer();
                else -> throw NotImplementedError("Rating Value: ${obj?.jsonPrimitive?.int}")
            };
    }
}