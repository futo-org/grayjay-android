package com.futo.platformplayer.engine.exceptions

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.getOrThrow
import java.lang.Exception


open class PluginEngineException(config: IV8PluginConfig, error: String, code: String? = null) : PluginException(config, error, null, code) {

}