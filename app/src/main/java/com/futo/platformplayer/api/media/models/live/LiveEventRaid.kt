package com.futo.platformplayer.api.media.models.live

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.getOrThrow

class LiveEventRaid: IPlatformLiveEvent {
    override val type: LiveEventType = LiveEventType.RAID;

    val targetName: String;
    val targetThumbnail: String;
    val targetUrl: String;

    constructor(name: String, url: String, thumbnail: String) {
        this.targetName = name;
        this.targetUrl = url;
        this.targetThumbnail = thumbnail;
    }

    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject) : LiveEventRaid {
            val contextName = "LiveEventRaid"
            return LiveEventRaid(
                obj.getOrThrow(config, "targetName", contextName),
                obj.getOrThrow(config, "targetUrl", contextName),
                obj.getOrThrow(config, "targetThumbnail", contextName));
        }
    }
}