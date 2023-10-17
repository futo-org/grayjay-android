package com.futo.platformplayer.engine.exceptions

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow

class ScriptCaptchaRequiredException(config: IV8PluginConfig, val url: String?, val body: String?, ex: Exception? = null, stack: String? = null, code: String? = null) : ScriptException(config, "Captcha required", ex, stack, code) {

    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject) : ScriptException {
            val contextName = "ScriptCaptchaRequiredException";
            return ScriptCaptchaRequiredException(config,
                obj.getOrDefault<String>(config, "url", contextName, null),
                obj.getOrDefault<String>(config, "body", contextName, null));
        }
    }
}