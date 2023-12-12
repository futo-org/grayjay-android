package com.futo.platformplayer.api.media.platforms.js.models

import android.net.Uri
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.modifier.IModifierOptions
import com.futo.platformplayer.api.media.models.modifier.IRequest
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.getOrDefault

@kotlinx.serialization.Serializable
class JSRequest : IRequest {
    private val _v8Url: String?;
    private val _v8Headers: Map<String, String>?;
    private val _v8Options: Options?;

    override var url: String? = null;
    override lateinit var headers: Map<String, String>;

    constructor(plugin: JSClient, url: String?, headers: Map<String, String>?, options: Options?, originalUrl: String?, originalHeaders: Map<String, String>?) {
        _v8Url = url;
        _v8Headers = headers;
        _v8Options = options;
        initialize(plugin, originalUrl, originalHeaders);
    }
    constructor(plugin: JSClient, obj: V8ValueObject, originalUrl: String?, originalHeaders: Map<String, String>?) {
        val contextName = "ModifyRequestResponse";
        val config = plugin.config;
        _v8Url = obj.getOrDefault<String>(config, "url", contextName, null);
        _v8Headers = obj.getOrDefault<Map<String, String>>(config, "headers", contextName, null);
        _v8Options = obj.getOrDefault<V8ValueObject>(config, "options", "JSRequestModifier.options", null)?.let {
            Options(config, it);
        }
        initialize(plugin, originalUrl, originalHeaders);
    }

    private fun initialize(plugin: JSClient, originalUrl: String?, originalHeaders: Map<String, String>?) {
        val config = plugin.config;
        url = _v8Url ?: originalUrl;
        headers = _v8Headers ?: originalHeaders ?: mapOf();

        if(_v8Options != null) {
            if(_v8Options.applyCookieClient != null && url != null) {
                val client = plugin.getHttpClientById(_v8Options.applyCookieClient);
                if(client != null) {
                    val toModifyHeaders = headers.toMutableMap();
                    client.applyHeaders(Uri.parse(url), toModifyHeaders, false, true);
                    headers = toModifyHeaders;
                }
            }
            if(_v8Options.applyAuthClient != null && url != null) {
                val client = plugin.getHttpClientById(_v8Options.applyAuthClient);
                if(client != null) {
                    val toModifyHeaders = headers.toMutableMap();
                    client.applyHeaders(Uri.parse(url), toModifyHeaders, true, false);
                    headers = toModifyHeaders;
                }
            }
        }
    }

    fun modify(plugin: JSClient, originalUrl: String?, originalHeaders: Map<String, String>?): JSRequest {
        return JSRequest(plugin, _v8Url, _v8Headers, _v8Options, originalUrl, originalHeaders);
    }


    @kotlinx.serialization.Serializable
    class Options: IModifierOptions {
        override val applyAuthClient: String?;
        override val applyCookieClient: String?;

        constructor(config: IV8PluginConfig, obj: V8ValueObject) {
            applyAuthClient = obj.getOrDefault(config, "applyAuthClient", "JSRequestModifier.options.applyAuthClient", null);
            applyCookieClient = obj.getOrDefault(config, "applyCookieClient", "JSRequestModifier.options.applyCookieClient", null);
        }
    }

    companion object {

    }
}