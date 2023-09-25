package com.futo.platformplayer.api.media.models.live

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.getOrThrow

class LiveEventEmojis: IPlatformLiveEvent {
    override val type: LiveEventType = LiveEventType.EMOJIS;

    val emojis: HashMap<String, String>;

    constructor(emojis: HashMap<String, String>) {
        this.emojis = emojis;
    }

    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject) : LiveEventEmojis {
            val contextName = "LiveEventEmojis"
            return LiveEventEmojis(
                obj.getOrThrow(config, "emojis", contextName));
        }
    }
}