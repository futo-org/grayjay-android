package com.futo.platformplayer.api.media.platforms.js.models

import android.net.Uri
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.engine.exceptions.ScriptImplementationException
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrNull
import com.futo.platformplayer.getOrThrow

class JSRequestModifier {
    private val _plugin: JSClient;
    private val _config: IV8PluginConfig;
    private var _modifier: V8ValueObject;
    val allowByteSkip: Boolean;

    constructor(plugin: JSClient, modifier: V8ValueObject) {
        this._plugin = plugin;
        this._modifier = modifier;
        this._config = plugin.config;
        val config = plugin.config;

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
        } as V8ValueObject;

        val req = JSRequest(_config, result);
        val options: V8ValueObject? = result.getOrDefault(_config, "options", "JSRequestModifier.options", null);
        if(options != null) {
            if(options.has("applyCookieClient")) {
                val clientId: String = options.getOrThrow(_config, "applyCookieClient", "JSRequestModifier.options.applyCookieClient", false)
                val client = _plugin.getHttpClientById(clientId);
                if(client != null) {
                    val toModifyHeaders = req.headers.toMutableMap();
                    client.applyHeaders(Uri.parse(req.url), toModifyHeaders, false, true);
                    req.headers = toModifyHeaders;
                }
            }
            if(options.has("applyAuthClient")) {
                val clientId: String = options.getOrThrow(_config, "applyAuthClient", "JSRequestModifier.options.applyAuthClient", false)
                val client = _plugin.getHttpClientById(clientId);
                if(client != null) {
                    val toModifyHeaders = req.headers.toMutableMap();
                    client.applyHeaders(Uri.parse(req.url), toModifyHeaders, true, false);
                    req.headers = toModifyHeaders;
                }
            }
        }
        return req;
    }

    interface IRequest {
        val url: String;
        val headers: Map<String, String>;
    }

    data class Request(override val url: String, override val headers: Map<String, String>) : IRequest;
}