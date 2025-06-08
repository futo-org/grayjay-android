package com.futo.platformplayer.api.media.models.streams.sources

import android.net.Uri
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource

class HLSVariantVideoUrlSource(
    override val name: String,
    override val width: Int,
    override val height: Int,
    override val container: String,
    override val codec: String,
    override val bitrate: Int?,
    override val duration: Long,
    override val priority: Boolean,
    val url: String
) : IVideoUrlSource {
    override fun getVideoUrl(): String {
        return url
    }
}

class HLSVariantAudioUrlSource(
    override val name: String,
    override val bitrate: Int,
    override val container: String,
    override val codec: String,
    override val language: String,
    override val duration: Long?,
    override val priority: Boolean,
    override val original: Boolean,
    val url: String
) : IAudioUrlSource {
    override fun getAudioUrl(): String {
        return url
    }
}

class HLSVariantSubtitleUrlSource(
    override val name: String,
    override val url: String,
    override val format: String,
) : ISubtitleSource {
    override val hasFetch: Boolean = false

    override fun getSubtitles(): String? {
        return null
    }

    override suspend fun getSubtitlesURI(): Uri? {
        return Uri.parse(url)
    }
}