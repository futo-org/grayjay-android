package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlWidevineSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow

class JSAudioUrlWidevineSource : JSAudioUrlSource, IAudioUrlWidevineSource {
    override val licenseHeaders: Map<String, String>?
    override val licenseUri: String
    override val decodeLicenseResponse: Boolean

    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(plugin: JSClient, obj: V8ValueObject) : super(plugin, obj) {
        val contextName = "JSAudioUrlWidevineSource"
        val config = plugin.config
        licenseHeaders =
            obj.getOrDefault<Map<String, String>>(config, "licenseHeaders", contextName, null)
        licenseUri = _obj.getOrThrow(config, "licenseUri", contextName)
        decodeLicenseResponse = _obj.getOrThrow(config, "decodeLicenseResponse", contextName)
    }

    override fun toString(): String {
        val url = getAudioUrl()
        return "(name=$name, container=$container, bitrate=$bitrate, codec=$codec, url=$url, language=$language, duration=$duration, licenseHeaders=${licenseHeaders.toString()}, licenseUri=$licenseUri)"
    }
}
