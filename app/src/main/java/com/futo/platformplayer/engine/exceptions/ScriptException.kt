package com.futo.platformplayer.engine.exceptions

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.ensureIsBusy
import com.futo.platformplayer.getOrThrow

open class ScriptException(config: IV8PluginConfig, error: String, ex: Exception? = null, stack: String? = null, code: String? = null) : ScriptExecutionException(config, error, ex, stack, code) {




    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject) : ScriptException {
            obj.ensureIsBusy();
            return ScriptException(config, obj.getOrThrow(config, "message", "ScriptException"));
        }
    }
}