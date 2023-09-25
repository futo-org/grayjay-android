package com.futo.platformplayer.api.media.models.ratings

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.getOrThrow

/**
 * A rating that has both likes and dislikes
 */
@kotlinx.serialization.Serializable
class RatingLikeDislikes(val likes: Long, val dislikes: Long) : IRating {

    override val type: RatingType = RatingType.LIKEDISLIKES;

    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject) : RatingLikeDislikes {
            return RatingLikeDislikes(obj.getOrThrow(config, "likes", "RatingLikeDislikes"), obj.getOrThrow(config, "dislikes", "RatingLikeDislikes"));
        }
    }
}