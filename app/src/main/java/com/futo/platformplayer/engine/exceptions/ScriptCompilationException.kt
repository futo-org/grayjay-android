package com.futo.platformplayer.engine.exceptions

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.ensureIsBusy
import com.futo.platformplayer.getOrThrow

class ScriptCompilationException(config: IV8PluginConfig, error: String, ex: Exception? = null, code: String? = null) : PluginException(config, error, ex, code) {

    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject) : ScriptCompilationException {
            obj.ensureIsBusy();
            return ScriptCompilationException(config, obj.getOrThrow(config, "message", "ScriptCompilationException"));
        }
    }
}