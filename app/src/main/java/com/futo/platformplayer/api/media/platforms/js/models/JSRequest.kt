package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.logging.Logger

@kotlinx.serialization.Serializable
class JSRequest : JSRequestModifier.IRequest {
    override val url: String;
    override var headers: Map<String, String>;

    constructor(config: IV8PluginConfig, obj: V8ValueObject) {
        val contextName = "ModifyRequestResponse";
        url = obj.getOrThrow(config, "url", contextName);
        headers = obj.getOrThrow(config, "headers", contextName);
    }
}