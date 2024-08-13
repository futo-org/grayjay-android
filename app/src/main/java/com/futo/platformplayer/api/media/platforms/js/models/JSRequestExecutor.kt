package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.primitive.V8ValueString
import com.caoccao.javet.values.reference.V8ValueObject
import com.caoccao.javet.values.reference.V8ValueTypedArray
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.engine.exceptions.ScriptImplementationException
import com.futo.platformplayer.getOrThrow
import kotlinx.serialization.Serializable
import java.util.Base64

class JSRequestExecutor {
    private val _plugin: JSClient;
    private val _config: IV8PluginConfig;
    private var _executor: V8ValueObject;

    constructor(plugin: JSClient, executor: V8ValueObject) {
        this._plugin = plugin;
        this._executor = executor;
        this._config = plugin.config;
        val config = plugin.config;

        if(!executor.has("executeRequest"))
            throw ScriptImplementationException(config, "RequestExecutor is missing executeRequest", null);
    }

    //TODO: Executor properties?
    fun executeRequest(url: String, headers: Map<String, String>): ByteArray {
        if (_executor.isClosed)
            throw IllegalStateException("Executor object is closed");

        val result = V8Plugin.catchScriptErrors<Any>(
            _config,
            "[${_config.name}] JSRequestExecutor",
            "builder.modifyRequest()"
        ) {
            _executor.invoke("executeRequest", url, headers);
        } as V8Value;

        try {
            if(result is V8ValueString)
                return Base64.getDecoder().decode(result.value);
            if(result is V8ValueTypedArray)
                return result.toBytes();
            if(result is V8ValueObject && result.has("type")) {
                val type = result.getOrThrow<Int>(_config, "type", "JSRequestModifier");
                when(type) {
                    //TODO: Buffer type?
                }
            }
            throw NotImplementedError("Executor result type not implemented?");
        }
        finally {
            result.close();
        }
    }
}

//TODO: are these available..?
@Serializable
class ExecutorParameters {
    var rangeStart: Int = -1;
    var rangeEnd: Int = -1;

    var segment: Int = -1;
}