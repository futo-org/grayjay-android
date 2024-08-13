package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrNull
import com.futo.platformplayer.getOrThrow

class JSDashManifestRawAudioSource : JSSource {
    val container : String = "application/dash+xml";
    val name : String;
    val manifest: String;

    constructor(plugin: JSClient, obj: V8ValueObject) : super(TYPE_DASH_RAW, plugin, obj) {
        val contextName = "DashSource";
        val config = plugin.config;
        name = _obj.getOrThrow(config, "name", contextName);
        manifest = _obj.getOrThrow(config, "manifest", contextName);
    }
}