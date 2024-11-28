package com.futo.platformplayer.views.video.datasources

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import com.futo.platformplayer.api.media.platforms.js.models.JSRequestExecutor
import java.util.UUID
import kotlin.io.encoding.ExperimentalEncodingApi

@UnstableApi
class PluginMediaDrmCallback(
    private val delegate: MediaDrmCallback,
    private val requestExecutor: JSRequestExecutor,
    private val licenseUrl: String
) : MediaDrmCallback by delegate {

    @ExperimentalEncodingApi
    override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): ByteArray {
        val pluginResponse = requestExecutor.executeRequest("POST", licenseUrl, request.data, mapOf())

        return pluginResponse
    }
}
