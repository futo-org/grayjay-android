package com.futo.platformplayer.api.media.models.streams.sources

import android.net.Uri
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.states.StateDownloads
import kotlinx.serialization.SerialName
import java.io.File

@kotlinx.serialization.Serializable
class LocalSubtitleSource : ISubtitleSource {
    override val name: String;
    //Kept in the saved data for older app versions; the file can move, so [url] resolves it each time.
    @SerialName("url")
    private val _url: String?;
    override val url: String? get() = Uri.fromFile(File(absoluteFilePath)).toString();
    override val format: String?;
    override val language: String?
    override val hasFetch: Boolean get() = false;

    val filePath: String;
    /** [filePath] resolved against the current download folder. */
    val absoluteFilePath: String get() = StateDownloads.instance.resolveFile(filePath).absolutePath;

    constructor(name: String, language: String?, format: String?, filePath: String) {
        this.name = name;
        this.format = format;
        this.language = language
        this.filePath = filePath;
        this._url = Uri.fromFile(File(filePath)).toString();
    }

    fun withFilePath(path: String): LocalSubtitleSource = LocalSubtitleSource(name, language, format, path);

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
                source.language,
                source.format,
                path
            );
        }
    }
}