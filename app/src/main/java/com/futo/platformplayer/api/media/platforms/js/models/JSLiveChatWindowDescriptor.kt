package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.PlatformID
import com.futo.platformplayer.api.media.models.PlatformAuthorLink
import com.futo.platformplayer.api.media.models.live.ILiveChatWindowDescriptor
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class JSLiveChatWindowDescriptor: ILiveChatWindowDescriptor {
    override val url: String;
    override val removeElements: List<String>;
    override val removeElementsInterval: List<String>;

    constructor(config: SourcePluginConfig, obj: V8ValueObject) {
        val contextName = "LiveChatWindowDescriptor";

        url = obj.getOrThrow(config, "url", contextName);
        removeElements = obj.getOrDefault(config, "removeElements", contextName, listOf()) ?: listOf();
        removeElementsInterval = obj.getOrDefault(config, "removeElementsInterval", contextName, listOf()) ?: listOf();
    }
}