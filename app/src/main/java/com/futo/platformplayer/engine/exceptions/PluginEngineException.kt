package com.futo.platformplayer.engine.exceptions

import com.futo.platformplayer.engine.IV8PluginConfig


open class PluginEngineException(config: IV8PluginConfig, error: String, code: String? = null) : PluginException(config, error, null, code) {

}