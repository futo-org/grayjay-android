package com.futo.platformplayer.api.media.models.streams.sources

import android.net.Uri
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource

@kotlinx.serialization.Serializable
class SubtitleRawSource(
    override val name: String,
    override val language: String?,
    override val format: String?,
    val _subtitles: String,
    override val url: String? = null,
    override val hasFetch: Boolean = true
) : ISubtitleSource {
    override fun getSubtitles(): String? {
        return _subtitles;
    }

    override suspend fun getSubtitlesURI(): Uri? {
        return null;
    }
}