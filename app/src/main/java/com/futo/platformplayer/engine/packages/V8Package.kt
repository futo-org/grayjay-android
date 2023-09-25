package com.futo.platformplayer.engine.packages

import com.futo.platformplayer.engine.V8Plugin

abstract class V8Package {
    @Transient
    protected val _plugin: V8Plugin;
    @Transient
    private val _scripts: MutableList<String> = mutableListOf();

    abstract val name: String;
    @Transient
    open val variableName: String? = null;

    constructor(v8Plugin: V8Plugin) {
        _plugin = v8Plugin;
    }

    fun withScript(str: String) {
        _scripts.add(str);
    }
    fun getScripts() : List<String> {
        return _scripts.toList();
    }
}