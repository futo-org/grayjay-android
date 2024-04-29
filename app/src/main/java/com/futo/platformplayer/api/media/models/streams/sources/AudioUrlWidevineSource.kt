package com.futo.platformplayer.api.media.models.streams.sources

import com.futo.platformplayer.api.media.models.streams.sources.other.IStreamMetaDataSource
import com.futo.platformplayer.api.media.models.streams.sources.other.StreamMetaData
import com.futo.platformplayer.others.Language

class AudioUrlWidevineSource(
    override val streamMetaData: StreamMetaData?,
    override val name: String,
    val url: String,
    override val bitrate: Int,
    override val container: String = "",
    override val codec: String = "",
    override val language: String = Language.UNKNOWN,
    override val duration: Long? = null,
    override var priority: Boolean = false,
    private val bearerToken: String,
    private val licenseUri: String
) : IAudioUrlWidevineSource, IStreamMetaDataSource {
    override fun getAudioUrl(): String {
        return url
    }

    override fun getBearerToken(): String {
        return bearerToken
    }

    override fun getLicenseUri(): String {
        return licenseUri
    }

    companion object {
        fun fromUrlSource(source: IAudioUrlWidevineSource?): AudioUrlWidevineSource? {
            if (source == null)
                return null

            val streamData = if (source is IStreamMetaDataSource)
                source.streamMetaData else null

            return AudioUrlWidevineSource(
                streamData,
                source.name,
                source.getAudioUrl(),
                source.bitrate,
                source.container,
                source.codec,
                source.language,
                source.duration,
                source.priority,
                source.getBearerToken(),
                source.getLicenseUri()
            )
        }
    }

}