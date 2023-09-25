package com.futo.platformplayer.api.media.models.live

import com.caoccao.javet.values.reference.V8ValueArray
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.ratings.RatingLikes
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow

class LiveEventComment: IPlatformLiveEvent, ILiveEventChatMessage {
    override val type: LiveEventType = LiveEventType.COMMENT;

    override val name: String;
    override val thumbnail: String?;
    override val message: String;

    val colorName: String?;
    val badges: List<String>;

    constructor(name: String, thumbnail: String?, message: String, colorName: String? = null, badges: List<String>? = null) {
        this.name = name;
        this.message = message;
        this.thumbnail = thumbnail;
        this.colorName = colorName;
        this.badges = badges ?: listOf();
    }

    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject) : LiveEventComment {
            val contextName = "LiveEventComment"

            val colorName = obj.getOrDefault<String>(config, "colorName", contextName, null);
            val badges = obj.getOrDefault<List<String>>(config, "badges", contextName, null);

            return LiveEventComment(
                obj.getOrThrow(config, "name", contextName),
                obj.getOrThrow(config, "thumbnail", contextName, true),
                obj.getOrThrow(config, "message", contextName),
                colorName, badges);
        }
    }
}