package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.primitive.V8ValueString
import com.caoccao.javet.values.primitive.V8ValueUndefined
import com.caoccao.javet.values.reference.V8ValueObject
import com.caoccao.javet.values.reference.V8ValueTypedArray
import com.futo.platformplayer.api.media.platforms.js.DevJSClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.engine.exceptions.PluginException
import com.futo.platformplayer.engine.exceptions.ScriptException
import com.futo.platformplayer.engine.exceptions.ScriptImplementationException
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateDeveloper
import kotlinx.serialization.Serializable
import java.util.Base64

class JSRequestExecutor {
    private val _plugin: JSClient;
    private val _config: IV8PluginConfig;
    private var _executor: V8ValueObject;
    val urlPrefix: String?;

    private val hasCleanup: Boolean;

    constructor(plugin: JSClient, executor: V8ValueObject) {
        this._plugin = plugin;
        this._executor = executor;
        this._config = plugin.config;
        val config = plugin.config;

        urlPrefix = executor.getOrDefault(config, "urlPrefix", "RequestExecutor", null);

        if(!executor.has("executeRequest"))
            throw ScriptImplementationException(config, "RequestExecutor is missing executeRequest", null);
        hasCleanup = executor.has("cleanup");
    }

    //TODO: Executor properties?
    @Throws(ScriptException::class)
    open fun executeRequest(method: String, url: String, body: ByteArray?, headers: Map<String, String>): ByteArray {
        if (_executor.isClosed)
            throw IllegalStateException("Executor object is closed");

        val result = if(_plugin is DevJSClient)
            StateDeveloper.instance.handleDevCall(_plugin.devID, "requestExecutor.executeRequest()") {
                V8Plugin.catchScriptErrors<Any>(
                    _config,
                    "[${_config.name}] JSRequestExecutor",
                    "builder.modifyRequest()"
                ) {
                    _executor.invoke("executeRequest", url, headers, method, body);
                } as V8Value;
            }
            else V8Plugin.catchScriptErrors<Any>(
                _config,
                "[${_config.name}] JSRequestExecutor",
                "builder.modifyRequest()"
            ) {
                _executor.invoke("executeRequest", url, headers, method, body);
            } as V8Value;

        try {
            if(result is V8ValueString) {
                val base64Result = Base64.getDecoder().decode(result.value);
                return base64Result;
            }
            if(result is V8ValueTypedArray) {
                val buffer = result.buffer;
                val byteBuffer = buffer.byteBuffer;
                val bytesResult = ByteArray(result.byteLength);
                byteBuffer.get(bytesResult, 0, result.byteLength);
                buffer.close();
                return bytesResult;
            }
            if(result is V8ValueObject && result.has("type")) {
                val type = result.getOrThrow<Int>(_config, "type", "JSRequestModifier");
                when(type) {
                    //TODO: Buffer type?
                }
            }
            if(result is V8ValueUndefined) {
                if(_plugin is DevJSClient)
                    StateDeveloper.instance.logDevException(_plugin.devID, "JSRequestExecutor.executeRequest returned illegal undefined");
                throw ScriptImplementationException(_config, "JSRequestExecutor.executeRequest returned illegal undefined", null);
            }
            throw NotImplementedError("Executor result type not implemented? " + result.javaClass.name);
        }
        finally {
            result.close();
        }
    }


    open fun cleanup() {
        if (!hasCleanup || _executor.isClosed)
            return;

       if(_plugin is DevJSClient)
            StateDeveloper.instance.handleDevCall(_plugin.devID, "requestExecutor.executeRequest()") {
                V8Plugin.catchScriptErrors<Any>(
                    _config,
                    "[${_config.name}] JSRequestExecutor",
                    "builder.modifyRequest()"
                ) {
                    _executor.invokeVoid("cleanup", null);
                };
            }
        else V8Plugin.catchScriptErrors<Any>(
            _config,
            "[${_config.name}] JSRequestExecutor",
            "builder.modifyRequest()"
        ) {
           _executor.invokeVoid("cleanup", null);
        };
    }

    protected fun finalize() {
        cleanup();
    }
}

//TODO: are these available..?
@Serializable
class ExecutorParameters {
    var rangeStart: Int = -1;
    var rangeEnd: Int = -1;

    var segment: Int = -1;
}