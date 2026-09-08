package com.futo.platformplayer.api.media.platforms.js.models.sources

import android.util.Base64
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlWidevineSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.models.JSRequestExecutor
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.invokeV8
import com.futo.platformplayer.invokeV8Void
import com.futo.platformplayer.requireSourcePlugin

class JSAudioUrlWidevineSource : JSAudioUrlSource, IAudioUrlWidevineSource {
    override val licenseUri: String
    override val hasLicenseRequestExecutor: Boolean
    override val serviceCertificate: ByteArray?

    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(plugin: JSClient, obj: V8ValueObject) : super(plugin, obj) {
        val contextName = "JSAudioUrlWidevineSource"
        val config = plugin.config

        licenseUri = _obj.getOrThrow(config, "licenseUri", contextName)
        hasLicenseRequestExecutor = plugin.busy { obj.has("getLicenseRequestExecutor") }
        serviceCertificate = _obj.getOrDefault<String>(config, "serviceCertificate", contextName, null)
            ?.let { Base64.decode(it, Base64.NO_PADDING or Base64.NO_WRAP) }
    }

    override fun getLicenseRequestExecutor(): JSRequestExecutor? {
        return _obj.requireSourcePlugin("JSAudioUrlWidevineSource.getLicenseRequestExecutor").busy {
            if (!hasLicenseRequestExecutor || _obj.isClosed)
                return@busy null

            val result = V8Plugin.catchScriptErrors<Any>(_config, "[${_config.name}] JSAudioUrlWidevineSource", "obj.getLicenseRequestExecutor()") {
                _obj.invokeV8("getLicenseRequestExecutor", arrayOf<Any>())
            }

            if (result !is V8ValueObject)
                return@busy null

            return@busy JSRequestExecutor(_plugin, result)
        }
    }

    override fun toString(): String {
        val url = getAudioUrl()
        return "(name=$name, container=$container, bitrate=$bitrate, codec=$codec, url=$url, language=$language, duration=$duration, hasLicenseRequestExecutor=${hasLicenseRequestExecutor}, licenseUri=$licenseUri)"
    }
}
