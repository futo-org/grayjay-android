package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlWidevineSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.getOrThrow

class JSAudioUrlWidevineSource : JSAudioUrlSource, IAudioUrlWidevineSource {
    override val bearerToken: String
    override val licenseUri: String

    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(plugin: JSClient, obj: V8ValueObject) : super(plugin, obj) {
        val contextName = "JSAudioUrlWidevineSource"
        val config = plugin.config
        bearerToken = _obj.getOrThrow(config, "bearerToken", contextName)
        licenseUri = _obj.getOrThrow(config, "licenseUri", contextName)
    }

    override fun toString(): String {
        val url = getAudioUrl()
        return "(name=$name, container=$container, bitrate=$bitrate, codec=$codec, url=$url, language=$language, duration=$duration, bearerToken=$bearerToken, licenseUri=$licenseUri)"
    }
}
