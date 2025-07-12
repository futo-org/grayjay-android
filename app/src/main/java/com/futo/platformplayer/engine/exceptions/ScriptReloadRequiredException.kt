package com.futo.platformplayer.engine.exceptions

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8PluginConfig
import com.futo.platformplayer.ensureIsBusy
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow

class ScriptReloadRequiredException(config: IV8PluginConfig, val msg: String?, val reloadData: String?, ex: Exception? = null, stack: String? = null, code: String? = null) : ScriptException(config, msg ?: "ReloadRequired", ex, stack, code) {

    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject) : ScriptException {
            obj.ensureIsBusy();
            val contextName = "ScriptReloadRequiredException";
            return ScriptReloadRequiredException(config,
                obj.getOrThrow(config, "message", contextName),
                obj.getOrDefault<String>(config, "reloadData", contextName, null));
        }
    }
}