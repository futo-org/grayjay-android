@file:Suppress("DEPRECATION")

package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.platforms.js.models.JSRequestModifier
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.orNull
import com.futo.platformplayer.views.video.datasources.JSHttpDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource

abstract class JSSource {
    protected val _config: IV8PluginConfig;
    protected val _obj: V8ValueObject;
    private val _hasRequestModifier: Boolean;

    val type : String;

    constructor(type: String, config: IV8PluginConfig, obj: V8ValueObject) {
        this._config = config;
        this._obj = obj;
        this.type = type;

        _hasRequestModifier = obj.has("getRequestModifier");
    }

    fun getRequestModifier(): JSRequestModifier? {
        if (!_hasRequestModifier || _obj.isClosed) {
            return null;
        }

        val result = V8Plugin.catchScriptErrors<Any>(_config, "[${_config.name}] JSVideoUrlSource", "obj.getRequestModifier()") {
            _obj.invoke("getRequestModifier", arrayOf<Any>());
        };

        if (result !is V8ValueObject) {
            return null;
        }

        return JSRequestModifier(_config, result)
    }

    fun getHttpDataSourceFactory(): HttpDataSource.Factory {
        val requestModifier = getRequestModifier();
        return if (requestModifier != null) {
            JSHttpDataSource.Factory().setRequestModifier(requestModifier);
        } else {
            DefaultHttpDataSource.Factory();
        }
    }

    companion object {
        const val TYPE_AUDIOURL = "AudioUrlSource";
        const val TYPE_VIDEOURL = "VideoUrlSource";
        const val TYPE_AUDIO_WITH_METADATA = "AudioUrlRangeSource";
        const val TYPE_VIDEO_WITH_METADATA = "VideoUrlRangeSource";
        const val TYPE_DASH = "DashSource";
        const val TYPE_HLS = "HLSSource";

        fun fromV8VideoNullable(config: IV8PluginConfig, obj: V8Value?) : IVideoSource? = obj.orNull { fromV8Video(config, it as V8ValueObject) };
        fun fromV8Video(config: IV8PluginConfig, obj: V8ValueObject) : IVideoSource {
            val type = obj.getString("plugin_type");
            return when(type) {
                TYPE_VIDEOURL -> JSVideoUrlSource(config, obj);
                TYPE_VIDEO_WITH_METADATA -> JSVideoUrlRangeSource(config, obj);
                TYPE_HLS -> fromV8HLS(config, obj);
                TYPE_DASH -> fromV8Dash(config, obj);
                else -> throw NotImplementedError("Unknown type ${type}");
            }
        }
        fun fromV8DashNullable(config: IV8PluginConfig, obj: V8Value?) : JSDashManifestSource? = obj.orNull { fromV8Dash(config, it as V8ValueObject) };
        fun fromV8Dash(config: IV8PluginConfig, obj: V8ValueObject) : JSDashManifestSource = JSDashManifestSource(config, obj);
        fun fromV8HLSNullable(config: IV8PluginConfig, obj: V8Value?) : JSHLSManifestSource? = obj.orNull { fromV8HLS(config, it as V8ValueObject) };
        fun fromV8HLS(config: IV8PluginConfig, obj: V8ValueObject) : JSHLSManifestSource = JSHLSManifestSource(config, obj);

        fun fromV8Audio(config: IV8PluginConfig, obj: V8ValueObject) : IAudioSource {
            val type = obj.getString("plugin_type");
            return when(type) {
                TYPE_HLS -> JSHLSManifestAudioSource.fromV8HLS(config, obj);
                TYPE_AUDIOURL -> JSAudioUrlSource(config, obj);
                TYPE_AUDIO_WITH_METADATA -> JSAudioUrlRangeSource(config, obj);
                else -> throw NotImplementedError("Unknown type ${type}");
            }
        }
    }
}