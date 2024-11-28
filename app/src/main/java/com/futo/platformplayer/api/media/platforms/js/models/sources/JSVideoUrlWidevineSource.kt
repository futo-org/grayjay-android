package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlWidevineSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.models.JSRequestExecutor
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrThrow

class JSVideoUrlWidevineSource : JSVideoUrlSource, IVideoUrlWidevineSource {
    override val licenseUri: String
    override val hasLicenseRequestExecutor: Boolean

    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(plugin: JSClient, obj: V8ValueObject) : super(plugin, obj) {
        val contextName = "JSAudioUrlWidevineSource"
        val config = plugin.config

        licenseUri = _obj.getOrThrow(config, "licenseUri", contextName)
        hasLicenseRequestExecutor = obj.has("getLicenseRequestExecutor")
    }

    override fun getLicenseRequestExecutor(): JSRequestExecutor? {
        if (!hasLicenseRequestExecutor || _obj.isClosed)
            return null

        val result = V8Plugin.catchScriptErrors<Any>(_config, "[${_config.name}] JSAudioUrlWidevineSource", "obj.getLicenseRequestExecutor()") {
            _obj.invoke("getLicenseRequestExecutor", arrayOf<Any>())
        }

        if (result !is V8ValueObject)
            return null

        return JSRequestExecutor(_plugin, result)
    }

    override fun toString(): String {
        val url = getVideoUrl()
        return "(width=$width, height=$height, container=$container, codec=$codec, name=$name, bitrate=$bitrate, duration=$duration, url=$url, hasLicenseRequestExecutor=$hasLicenseRequestExecutor, licenseUri=$licenseUri)"
    }
}