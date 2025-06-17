package com.futo.platformplayer.engine.exceptions

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.ensureIsBusy
import com.futo.platformplayer.getOrThrow

class ScriptImplementationException(config: IV8PluginConfig, error: String, ex: Exception? = null, var pluginId: String? = null, code: String? = null) : PluginException(config, error, ex, code) {

    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject) : ScriptImplementationException {
            obj.ensureIsBusy();
            return ScriptImplementationException(config, obj.getOrThrow(config, "message", "ScriptImplementationException"));
        }
    }
}