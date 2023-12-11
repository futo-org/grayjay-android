package com.futo.platformplayer.serializers

import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.api.media.models.ratings.RatingScaler
import com.futo.platformplayer.api.media.models.ratings.RatingType
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


class IRatingSerializer() : JsonContentPolymorphicSerializer<IRating>(IRating::class) {

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<IRating> {
        val obj = element.jsonObject["type"];
        return if(obj?.jsonPrimitive?.isString != false) {
            when (obj?.jsonPrimitive?.contentOrNull) {
                "LIKES" -> RatingLikes.serializer();
                "LIKEDISLIKES" -> RatingLikeDislikes.serializer();
                "SCALE" -> RatingScaler.serializer();
                else -> throw NotImplementedError("Rating Value: ${obj?.jsonPrimitive?.contentOrNull}")
            };
        } else {
            when (element.jsonObject["type"]?.jsonPrimitive?.int) {
                RatingType.LIKES.value -> RatingLikes.serializer();
                RatingType.LIKEDISLIKES.value -> RatingLikeDislikes.serializer();
                RatingType.SCALE.value -> RatingScaler.serializer();
                else -> throw NotImplementedError("Rating Value: ${obj.jsonPrimitive.int}")
            };
        }
    }
}