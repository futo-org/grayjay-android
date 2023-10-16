package com.futo.platformplayer.engine.exceptions

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.getOrThrow
import java.lang.Exception


class PluginEngineStoppedException(config: IV8PluginConfig, error: String, code: String? = null) : PluginEngineException(config, error, code) {

}