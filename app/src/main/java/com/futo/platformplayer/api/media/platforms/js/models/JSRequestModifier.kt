package com.futo.platformplayer.api.media.platforms.js.models

import android.net.Uri
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.modifier.IRequest
import com.futo.platformplayer.api.media.models.modifier.IRequestModifier
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.engine.exceptions.ScriptImplementationException
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrNull
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.invokeV8
import com.futo.platformplayer.invokeV8Void

class JSRequestModifier: IRequestModifier {
    private val _plugin: JSClient;
    private val _config: IV8PluginConfig;
    private var _modifier: V8ValueObject;
    override var allowByteSkip: Boolean = false;

    constructor(plugin: JSClient, modifier: V8ValueObject) {
        this._plugin = plugin;
        this._modifier = modifier;
        this._config = plugin.config;
        val config = plugin.config;

        plugin.busy {
            allowByteSkip = modifier.getOrNull(config, "allowByteSkip", "JSRequestModifier") ?: true;

            if(!modifier.has("modifyRequest"))
                throw ScriptImplementationException(config, "RequestModifier is missing modifyRequest", null);
        }

    }

    override fun modifyRequest(url: String, headers: Map<String, String>): IRequest {
        if (_modifier.isClosed) {
            return Request(url, headers);
        }

        return _plugin.busy {
            val result = V8Plugin.catchScriptErrors<Any>(_config, "[${_config.name}] JSRequestModifier", "builder.modifyRequest()") {
                _modifier.invokeV8("modifyRequest", url, headers);
            } as V8ValueObject;

            val req = JSRequest(_plugin, result, url, headers);
            result.close();
            return@busy req;
        }
    }


    data class Request(override val url: String, override val headers: Map<String, String>) : IRequest;
}