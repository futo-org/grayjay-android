package com.futo.platformplayer.api.media.models.live

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.ensureIsBusy
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow

class LiveEventRaid: IPlatformLiveEvent {
    override val type: LiveEventType = LiveEventType.RAID;

    val targetName: String;
    val targetThumbnail: String;
    val targetUrl: String;
    val isOutgoing: Boolean;

    override var time: Long = -1;

    constructor(name: String, url: String, thumbnail: String, isOutgoing: Boolean) {
        this.targetName = name;
        this.targetUrl = url;
        this.targetThumbnail = thumbnail;
        this.isOutgoing = isOutgoing;
    }

    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject) : LiveEventRaid {
            obj.ensureIsBusy();
            val contextName = "LiveEventRaid"
            return LiveEventRaid(
                obj.getOrThrow(config, "targetName", contextName),
                obj.getOrThrow(config, "targetUrl", contextName),
                obj.getOrThrow(config, "targetThumbnail", contextName),
                obj.getOrDefault<Boolean>(config, "isOutgoing", contextName, true) ?: true);
        }
    }
}