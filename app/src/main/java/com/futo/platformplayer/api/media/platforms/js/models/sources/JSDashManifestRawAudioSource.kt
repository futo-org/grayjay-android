package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.platforms.js.DevJSClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrNull
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.others.Language
import com.futo.platformplayer.states.StateDeveloper

class JSDashManifestRawAudioSource : JSSource, IAudioSource {
    override val container : String = "application/dash+xml";
    override val name : String;
    override val codec: String;
    override val bitrate: Int;
    override val duration: Long;
    override val priority: Boolean;

    override val language: String;

    val url: String;
    var manifest: String?;

    val hasGenerate: Boolean;

    constructor(plugin: JSClient, obj: V8ValueObject) : super(TYPE_DASH_RAW, plugin, obj) {
        val contextName = "DashRawSource";
        val config = plugin.config;
        name = _obj.getOrThrow(config, "name", contextName);
        url = _obj.getOrThrow(config, "url", contextName);
        manifest = _obj.getOrThrow(config, "manifest", contextName);
        codec = _obj.getOrDefault(config, "codec", contextName, "") ?: "";
        bitrate = _obj.getOrDefault(config, "bitrate", contextName, 0) ?: 0;
        duration = _obj.getOrDefault(config, "duration", contextName, 0) ?: 0;
        priority = _obj.getOrDefault(config, "priority", contextName, false) ?: false;
        language = _obj.getOrDefault(config, "language", contextName, Language.UNKNOWN) ?: Language.UNKNOWN;
        hasGenerate = _obj.has("generate");
    }

    fun generate(): String? {
        if(!hasGenerate)
            return manifest;
        if(_obj.isClosed)
            throw IllegalStateException("Source object already closed");

        val plugin = _plugin.getUnderlyingPlugin();
        if(_plugin is DevJSClient)
            return StateDeveloper.instance.handleDevCall(_plugin.devID, "DashManifestRaw", false) {
                _plugin.getUnderlyingPlugin().catchScriptErrors("DashManifestRaw", "dashManifestRaw.generate()") {
                    _obj.invokeString("generate");
                }
            }
        else
            return _plugin.getUnderlyingPlugin().catchScriptErrors("DashManifestRaw", "dashManifestRaw.generate()") {
                _obj.invokeString("generate");
            }
    }
}