package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.live.IPlatformLiveEvent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.structures.IPlatformLiveEventPager
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrThrow

class JSLiveEventPager : JSPager<IPlatformLiveEvent>, IPlatformLiveEventPager {
    override var nextRequest: Int;

    constructor(config: SourcePluginConfig, plugin: JSClient, pager: V8ValueObject) : super(config, plugin, pager) {
        nextRequest = pager.getOrThrow(config, "nextRequest", "LiveEventPager");
    }

    override fun nextPage() = plugin.isBusyWith("JSLiveEventPager.nextPage") {
        super.nextPage();
        nextRequest = pager.getOrThrow(config, "nextRequest", "LiveEventPager");
    }

    override fun convertResult(obj: V8ValueObject): IPlatformLiveEvent {
        return IPlatformLiveEvent.fromV8(config, obj, "LiveEventPager");
    }
}