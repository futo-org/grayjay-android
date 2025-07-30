package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.live.IPlatformLiveEvent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.structures.IPlatformLiveEventPager
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.invokeV8
import com.futo.platformplayer.warnIfMainThread
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class JSVODEventPager : JSPager<IPlatformLiveEvent>, IPlatformLiveEventPager {
    override var nextRequest: Int;

    constructor(config: SourcePluginConfig, plugin: JSClient, pager: V8ValueObject) : super(config, plugin, pager) {
        nextRequest = pager.getOrThrow(config, "nextRequest", "LiveEventPager");
    }

    fun nextPage(ms: Int) = plugin.isBusyWith("JSLiveEventPager.nextPage") {
        warnIfMainThread("VODEventPager.nextPage");

        val pluginV8 = plugin.getUnderlyingPlugin();
        pluginV8.busy {
            val newPager: V8Value = pluginV8.catchScriptErrors("[${plugin.config.name}] JSPager", "pager.nextPage(...)") {
                pager.invokeV8<V8Value>("nextPage", ms);
            };
            if(newPager is V8ValueObject)
                pager = newPager;
            _hasMorePages = pager.getOrDefault(config, "hasMore", "Pager", false) ?: false;
            _resultChanged = true;
        }
        nextRequest = pager.getOrThrow(config, "nextRequest", "LiveEventPager");
    }

    override fun nextPage() = nextPage(0);

    override fun convertResult(obj: V8ValueObject): IPlatformLiveEvent {
        return IPlatformLiveEvent.fromV8(config, obj, "LiveEventPager");
    }
}