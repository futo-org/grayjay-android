package com.futo.platformplayer.engine.packages

import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.states.StateApp


class PackageJSDOM : V8Package {
    @Transient
    private val _config: IV8PluginConfig;

    override val name: String get() = "JSDOM";
    override val variableName: String get() = "packageJSDOM";

    constructor(plugin: V8Plugin, config: IV8PluginConfig): super(plugin) {
        _config = config;
        plugin.withDependency(StateApp.instance.contextOrNull ?: return, "scripts/JSDOM.js");
    }

}