package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IDashManifestWidevineSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.models.JSRequestExecutor
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrNull
import com.futo.platformplayer.getOrThrow

class JSDashManifestWidevineSource : IVideoUrlSource, IDashManifestSource,
    IDashManifestWidevineSource, JSSource {
    override val width: Int = 0
    override val height: Int = 0
    override val container: String = "application/dash+xml"
    override val codec: String = "Dash"
    override val name: String
    override val bitrate: Int? = null
    override val url: String
    override val duration: Long

    override var priority: Boolean = false

    override val licenseUri: String
    override val hasLicenseRequestExecutor: Boolean

    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(plugin: JSClient, obj: V8ValueObject) : super(TYPE_DASH, plugin, obj) {
        val contextName = "DashWidevineSource"
        val config = plugin.config
        name = _obj.getOrThrow(config, "name", contextName)
        url = _obj.getOrThrow(config, "url", contextName)
        duration = _obj.getOrThrow(config, "duration", contextName)

        priority = obj.getOrNull(config, "priority", contextName) ?: false

        licenseUri = _obj.getOrThrow(config, "licenseUri", contextName)
        hasLicenseRequestExecutor = obj.has("getLicenseRequestExecutor")
    }

    override fun getLicenseRequestExecutor(): JSRequestExecutor? {
        if (!hasLicenseRequestExecutor || _obj.isClosed)
            return null

        val result = V8Plugin.catchScriptErrors<Any>(_config, "[${_config.name}] JSDashManifestWidevineSource", "obj.getLicenseRequestExecutor()") {
            _obj.invoke("getLicenseRequestExecutor", arrayOf<Any>())
        }

        if (result !is V8ValueObject)
            return null

        return JSRequestExecutor(_plugin, result)
    }

    override fun getVideoUrl(): String {
        return url
    }
}