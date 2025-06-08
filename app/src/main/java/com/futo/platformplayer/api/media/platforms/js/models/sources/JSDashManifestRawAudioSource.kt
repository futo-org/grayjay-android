package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.other.IStreamMetaDataSource
import com.futo.platformplayer.api.media.models.streams.sources.other.StreamMetaData
import com.futo.platformplayer.api.media.platforms.js.DevJSClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrNull
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.others.Language
import com.futo.platformplayer.states.StateDeveloper

class JSDashManifestRawAudioSource : JSSource, IAudioSource, IJSDashManifestRawSource, IStreamMetaDataSource {
    override val container : String;
    override val name : String;
    override val codec: String;
    override val bitrate: Int;
    override val duration: Long;
    override val priority: Boolean;
    override var original: Boolean = false;

    override val language: String;

    val url: String;
    override var manifest: String?;

    override val hasGenerate: Boolean;

    override var streamMetaData: StreamMetaData? = null;

    constructor(plugin: JSClient, obj: V8ValueObject) : super(TYPE_DASH_RAW, plugin, obj) {
        val contextName = "DashRawSource";
        val config = plugin.config;
        name = _obj.getOrThrow(config, "name", contextName);
        url = _obj.getOrThrow(config, "url", contextName);
        container = _obj.getOrDefault<String>(config, "container", contextName, null) ?: "application/dash+xml";
        manifest = _obj.getOrThrow(config, "manifest", contextName);
        codec = _obj.getOrDefault(config, "codec", contextName, "") ?: "";
        bitrate = _obj.getOrDefault(config, "bitrate", contextName, 0) ?: 0;
        duration = _obj.getOrDefault(config, "duration", contextName, 0) ?: 0;
        priority = _obj.getOrDefault(config, "priority", contextName, false) ?: false;
        language = _obj.getOrDefault(config, "language", contextName, Language.UNKNOWN) ?: Language.UNKNOWN;
        original =  if(_obj.has("original")) obj.getOrThrow(config, "original", contextName) else false;
        hasGenerate = _obj.has("generate");
    }

    override fun generate(): String? {
        if(!hasGenerate)
            return manifest;
        if(_obj.isClosed)
            throw IllegalStateException("Source object already closed");

        val plugin = _plugin.getUnderlyingPlugin();

        var result: String? = null;
        if(_plugin is DevJSClient)
            result = StateDeveloper.instance.handleDevCall(_plugin.devID, "DashManifestRaw", false) {
                _plugin.getUnderlyingPlugin().catchScriptErrors("DashManifestRaw", "dashManifestRaw.generate()") {
                    _obj.invokeString("generate");
                }
            }
        else
            result = _plugin.getUnderlyingPlugin().catchScriptErrors("DashManifestRaw", "dashManifestRaw.generate()") {
                _obj.invokeString("generate");
            }

        if(result != null){
            val initStart = _obj.getOrDefault<Int>(_config, "initStart", "JSDashManifestRawSource", null) ?: 0;
            val initEnd = _obj.getOrDefault<Int>(_config, "initEnd", "JSDashManifestRawSource", null) ?: 0;
            val indexStart = _obj.getOrDefault<Int>(_config, "indexStart", "JSDashManifestRawSource", null) ?: 0;
            val indexEnd = _obj.getOrDefault<Int>(_config, "indexEnd", "JSDashManifestRawSource", null) ?: 0;
            if(initEnd > 0 && indexStart > 0 && indexEnd > 0) {
                streamMetaData = StreamMetaData(initStart, initEnd, indexStart, indexEnd);
            }
        }
        return result;
    }
}