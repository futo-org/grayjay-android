package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.engine.V8Plugin

class JSChannelPager : JSPager<PlatformAuthorLink>, IPager<PlatformAuthorLink> {

    constructor(config: SourcePluginConfig, plugin: V8Plugin, pager: V8ValueObject) : super(config, plugin, pager) {}

    override fun convertResult(obj: V8ValueObject): PlatformAuthorLink {
        return PlatformAuthorLink.fromV8(config, obj);
    }
}