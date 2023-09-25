package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.engine.exceptions.ScriptImplementationException
import com.futo.platformplayer.getOrNull

class JSRequestModifier {
    private val _config: IV8PluginConfig;
    private var _modifier: V8ValueObject;
    val allowByteSkip: Boolean;

    constructor(config: IV8PluginConfig, modifier: V8ValueObject) {
        this._modifier = modifier;
        this._config = config;

        allowByteSkip = modifier.getOrNull(config, "allowByteSkip", "JSRequestModifier") ?: true;

        if(!modifier.has("modifyRequest"))
            throw ScriptImplementationException(config, "RequestModifier is missing modifyRequest", null);
    }

    fun modifyRequest(url: String, headers: Map<String, String>): IRequest {
        if (_modifier.isClosed) {
            return Request(url, headers);
        }

        val result = V8Plugin.catchScriptErrors<Any>(_config, "[${_config.name}] JSRequestModifier", "builder.modifyRequest()") {
            _modifier.invoke("modifyRequest", url, headers);
        };

        return JSRequest(_config, result as V8ValueObject);
    }

    interface IRequest {
        val url: String;
        val headers: Map<String, String>;
    }

    data class Request(override val url: String, override val headers: Map<String, String>) : IRequest;
}