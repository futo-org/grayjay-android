package com.futo.platformplayer.engine.exceptions

import com.futo.platformplayer.engine.IV8PluginConfig


class PluginEngineStoppedException(config: IV8PluginConfig, error: String, code: String? = null) : PluginEngineException(config, error, code) {

}