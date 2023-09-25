package com.futo.platformplayer.api.media.models.live

import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.ratings.IRating
import com.futo.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.api.media.models.ratings.RatingScaler
import com.futo.platformplayer.api.media.models.ratings.RatingType
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.orDefault

interface IPlatformLiveEvent {
    val type : LiveEventType;


    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject, contextName: String = "Unknown") : IPlatformLiveEvent {
            val contextName = "LiveEvent";
            val type = LiveEventType.fromInt(obj.getOrThrow<Int>(config, "type", contextName));
            return when(type) {
                LiveEventType.COMMENT -> LiveEventComment.fromV8(config, obj);
                LiveEventType.EMOJIS -> LiveEventEmojis.fromV8(config, obj);
                LiveEventType.DONATION -> LiveEventDonation.fromV8(config, obj);
                LiveEventType.VIEWCOUNT -> LiveEventViewCount.fromV8(config, obj);
                LiveEventType.RAID -> LiveEventRaid.fromV8(config, obj);
                else -> throw NotImplementedError("Unknown type ${type}");
            }
        }
    }
}