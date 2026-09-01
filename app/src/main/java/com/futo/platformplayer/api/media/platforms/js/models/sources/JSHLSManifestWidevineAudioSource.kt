package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestWidevineAudioSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.models.JSRequestExecutor
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrNull
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.invokeV8
import com.futo.platformplayer.requireSourcePlugin

class JSHLSManifestWidevineAudioSource : IHLSManifestWidevineAudioSource, JSSource {
    override val container: String get() = "application/vnd.apple.mpegurl";
    override val codec: String = "HLS";
    override val name: String;
    override val bitrate: Int = 0;
    override val url: String;
    override val duration: Long;
    override val language: String;

    override var priority: Boolean = false;
    override var original: Boolean = false;

    override val licenseUri: String;
    override val hasLicenseRequestExecutor: Boolean;

    constructor(plugin: JSClient, obj: V8ValueObject) : super(TYPE_HLS, plugin, obj) {
        val contextName = "HLSWidevineAudioSource";
        val config = plugin.config;

        name = _obj.getOrThrow(config, "name", contextName);
        url = _obj.getOrThrow(config, "url", contextName);
        duration = _obj.getOrThrow<Int>(config, "duration", contextName).toLong();
        language = _obj.getOrThrow(config, "language", contextName);

        priority = obj.getOrNull(config, "priority", contextName) ?: false;
        original = obj.getOrNull(config, "original", contextName) ?: false;

        licenseUri = _obj.getOrThrow(config, "licenseUri", contextName);
        hasLicenseRequestExecutor = plugin.busy { obj.has("getLicenseRequestExecutor") };
    }

    override fun getLicenseRequestExecutor(): JSRequestExecutor? {
        return _obj.requireSourcePlugin("JSHLSManifestWidevineAudioSource.getLicenseRequestExecutor").busy {
            if (!hasLicenseRequestExecutor || _obj.isClosed)
                return@busy null

            val result = V8Plugin.catchScriptErrors<Any>(_config, "[${_config.name}] JSHLSManifestWidevineAudioSource", "obj.getLicenseRequestExecutor()") {
                _obj.invokeV8("getLicenseRequestExecutor", arrayOf<Any>())
            }

            if (result !is V8ValueObject)
                return@busy null

            return@busy JSRequestExecutor(_plugin, result)
        }
    }
}
