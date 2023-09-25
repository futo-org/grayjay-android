package com.futo.platformplayer.api.media.models.ratings

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.getOrThrow

/**
 * A rating that is based on a scaler (0..1)
 */
@kotlinx.serialization.Serializable
class RatingScaler(val value: Float) : IRating {
    override val type: RatingType = RatingType.SCALE;

    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject) : RatingScaler {
            return RatingScaler(obj.getOrThrow(config, "value", "RatingScaler"));
        }
    }
}