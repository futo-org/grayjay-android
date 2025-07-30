package com.futo.platformplayer.api.media.models.live

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.ensureIsBusy
import com.futo.platformplayer.getOrThrow

class LiveEventViewCount: IPlatformLiveEvent {
    override val type: LiveEventType = LiveEventType.VIEWCOUNT;

    val viewCount: Int;

    override var time: Long = -1;

    constructor(viewCount: Int) {
        this.viewCount = viewCount;
    }

    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject) : LiveEventViewCount {
            obj.ensureIsBusy();
            val contextName = "LiveEventViewCount"
            return LiveEventViewCount(
                obj.getOrThrow(config, "viewCount", contextName));
        }
    }
}