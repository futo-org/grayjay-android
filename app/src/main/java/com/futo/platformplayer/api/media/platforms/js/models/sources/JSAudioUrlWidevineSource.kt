package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlWidevineSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.getOrThrow

class JSAudioUrlWidevineSource(plugin: JSClient, obj: V8ValueObject) : IAudioUrlWidevineSource,
    JSAudioUrlSource(plugin, obj) {
    private val bearerToken: String
    private val licenseUri: String

    override fun getBearerToken(): String {
        return bearerToken
    }

    override fun getLicenseUri(): String {
        return licenseUri
    }

    init {
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