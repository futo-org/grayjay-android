package com.futo.platformplayer.views.video.datasources

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@UnstableApi
class Base64MediaDrmCallback(
    private val delegate: MediaDrmCallback,
) : MediaDrmCallback by delegate {

    @ExperimentalEncodingApi
    override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): ByteArray {
        val originalResponse = delegate.executeKeyRequest(uuid, request)
        val decodedData: ByteArray = Base64.decode(originalResponse)

        return decodedData
    }
}
