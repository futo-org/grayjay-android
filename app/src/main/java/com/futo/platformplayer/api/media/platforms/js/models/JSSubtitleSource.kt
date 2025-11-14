package com.futo.platformplayer.api.media.platforms.js.models

import android.net.Uri
import com.caoccao.javet.values.primitive.V8ValueString
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.subtitles.ISubtitleSource
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.getSourcePlugin
import com.futo.platformplayer.invokeV8
import com.futo.platformplayer.states.StateApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class JSSubtitleSource : ISubtitleSource {
    private val _obj: V8ValueObject;

    private val _lockSub = Object();
    private var _fileSubtitle: File? = null;

    override val name: String;
    override val url: String?;
    override val format: String?;
    override val language: String?
    override val hasFetch: Boolean;

    constructor(config: SourcePluginConfig, v8Value: V8ValueObject) {
        _obj = v8Value;

        val context = "JSSubtitles";
        name = v8Value.getOrThrow(config, "name", context, false);
        language = v8Value.getOrDefault(config, "language", context, null);
        url = v8Value.getOrThrow(config, "url", context, true);
        format = v8Value.getOrThrow(config, "format", context, true);
        hasFetch = v8Value.has("getSubtitles");
    }

    override fun getSubtitles(): String {
        if(!hasFetch)
            throw IllegalStateException("This subtitle doesn't support getSubtitles..");

        return _obj.getSourcePlugin()?.busy {
            val v8String = _obj.invokeV8<V8ValueString>("getSubtitles", arrayOf<Any>());
            return@busy v8String.value;
        } ?: "";
    }

    override suspend fun getSubtitlesURI(): Uri? {
        if(_fileSubtitle != null)
            return Uri.fromFile(_fileSubtitle);
        if(!hasFetch)
            return Uri.parse(url);

        return withContext(Dispatchers.IO) {
            return@withContext synchronized(_lockSub) {
                val subtitleText = getSubtitles();
                val subFile = StateApp.instance.getTempFile();
                subFile.writeText(subtitleText, Charsets.UTF_8);
                _fileSubtitle = subFile;
                return@synchronized Uri.fromFile(subFile);
            };
        }
    }

    companion object {
        fun fromV8(config: SourcePluginConfig, value: V8ValueObject): JSSubtitleSource {
            return JSSubtitleSource(config, value);
        }
    }
}