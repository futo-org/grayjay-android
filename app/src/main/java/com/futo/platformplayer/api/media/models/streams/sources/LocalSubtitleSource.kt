package com.futo.platformplayer.api.media.models.streams.sources

import android.net.Uri
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import java.io.File

@kotlinx.serialization.Serializable
class LocalSubtitleSource : ISubtitleSource {
    override val name: String;
    override val url: String?;
    override val format: String?;
    override val hasFetch: Boolean get() = false;

    val filePath: String;

    constructor(name: String, format: String?, filePath: String) {
        this.name = name;
        this.format = format;
        this.filePath = filePath;
        this.url = Uri.fromFile(File(filePath)).toString();
    }

    override fun getSubtitles(): String? {
        return null;
    }

    override suspend fun getSubtitlesURI(): Uri? {
        return null;
    }

    companion object {
        fun fromSource(source: SubtitleRawSource, path: String): LocalSubtitleSource {
            return LocalSubtitleSource(
                source.name,
                source.format,
                path
            );
        }
    }
}