package com.futo.platformplayer.api.media.platforms.js.models.sources

import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.modifier.AdhocRequestModifier
import com.futo.platformplayer.api.media.models.modifier.IRequestModifier
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.models.JSRequest
import com.futo.platformplayer.api.media.platforms.js.models.JSRequestExecutor
import com.futo.platformplayer.api.media.platforms.js.models.JSRequestModifier
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.orNull
import com.futo.platformplayer.views.video.datasources.JSHttpDataSource

abstract class JSSource {
    protected val _plugin: JSClient;
    protected val _config: IV8PluginConfig;
    protected val _obj: V8ValueObject;

    val hasRequestModifier: Boolean;
    private val _requestModifier: JSRequest?;

    val hasRequestExecutor: Boolean;
    private val _requestExecutor: JSRequest?;

    val requiresCustomDatasource: Boolean get() {
        return hasRequestModifier || hasRequestExecutor;
    }

    val type : String;

    constructor(type: String, plugin: JSClient, obj: V8ValueObject) {
        this._plugin = plugin;
        this._config = plugin.config;
        this._obj = obj;
        this.type = type;

        _requestModifier = obj.getOrDefault<V8ValueObject>(_config, "requestModifier", "JSSource.requestModifier", null)?.let {
            JSRequest(plugin, it, null, null, true);
        }
        hasRequestModifier = _requestModifier != null || obj.has("getRequestModifier");

        _requestExecutor = obj.getOrDefault<V8ValueObject>(_config, "requestExecutor", "JSSource.requestExecutor", null)?.let {
            JSRequest(plugin, it, null, null, true);
        }
        hasRequestExecutor = _requestExecutor != null || obj.has("getRequestExecutor");
    }

    fun getRequestModifier(): IRequestModifier? {
        if(_requestModifier != null)
            return AdhocRequestModifier { url, headers ->
                  return@AdhocRequestModifier _requestModifier.modify(_plugin, url, headers);
            };

        if (!hasRequestModifier || _obj.isClosed)
            return null;

        val result = V8Plugin.catchScriptErrors<Any>(_config, "[${_config.name}] JSVideoUrlSource", "obj.getRequestModifier()") {
            _obj.invoke("getRequestModifier", arrayOf<Any>());
        };

        if (result !is V8ValueObject)
            return null;

        return JSRequestModifier(_plugin, result)
    }
    open fun getRequestExecutor(): JSRequestExecutor? {
        if (!hasRequestExecutor || _obj.isClosed)
            return null;

        val result = V8Plugin.catchScriptErrors<Any>(_config, "[${_config.name}] JSSource", "obj.getRequestExecutor()") {
            _obj.invoke("getRequestExecutor", arrayOf<Any>());
        };

        if (result !is V8ValueObject)
            return null;

        return JSRequestExecutor(_plugin, result)
    }

    fun getUnderlyingPlugin(): JSClient? {
        return _plugin;
    }
    fun getUnderlyingObject(): V8ValueObject? {
        return _obj;
    }

    companion object {
        const val TYPE_AUDIOURL = "AudioUrlSource";
        const val TYPE_VIDEOURL = "VideoUrlSource";
        const val TYPE_AUDIO_WITH_METADATA = "AudioUrlRangeSource";
        const val TYPE_VIDEO_WITH_METADATA = "VideoUrlRangeSource";
        const val TYPE_DASH = "DashSource";
        const val TYPE_DASH_WIDEVINE = "DashWidevineSource";
        const val TYPE_DASH_RAW = "DashRawSource";
        const val TYPE_DASH_RAW_AUDIO = "DashRawAudioSource";
        const val TYPE_HLS = "HLSSource";
        const val TYPE_AUDIOURL_WIDEVINE = "AudioUrlWidevineSource"
        const val TYPE_VIDEOURL_WIDEVINE = "VideoUrlWidevineSource"

        fun fromV8VideoNullable(plugin: JSClient, obj: V8Value?) : IVideoSource? = obj.orNull { fromV8Video(plugin, it as V8ValueObject) };
        fun fromV8Video(plugin: JSClient, obj: V8ValueObject) : IVideoSource? {
            val type = obj.getString("plugin_type");
            return when(type) {
                TYPE_VIDEOURL -> JSVideoUrlSource(plugin, obj);
                TYPE_VIDEOURL_WIDEVINE -> JSVideoUrlWidevineSource(plugin, obj);
                TYPE_VIDEO_WITH_METADATA -> JSVideoUrlRangeSource(plugin, obj);
                TYPE_HLS -> fromV8HLS(plugin, obj);
                TYPE_DASH_WIDEVINE -> JSDashManifestWidevineSource(plugin, obj)
                TYPE_DASH -> fromV8Dash(plugin, obj);
                TYPE_DASH_RAW -> fromV8DashRaw(plugin, obj);
                else -> {
                    Logger.w("JSSource", "Unknown video type ${type}");
                    null;
                };
            }
        }
        fun fromV8DashNullable(plugin: JSClient, obj: V8Value?) : JSDashManifestSource? = obj.orNull { fromV8Dash(plugin, it as V8ValueObject) };
        fun fromV8Dash(plugin: JSClient, obj: V8ValueObject) : JSDashManifestSource = JSDashManifestSource(plugin, obj);
        fun fromV8DashRaw(plugin: JSClient, obj: V8ValueObject) : JSDashManifestRawSource = JSDashManifestRawSource(plugin, obj);
        fun fromV8DashRawAudio(plugin: JSClient, obj: V8ValueObject) : JSDashManifestRawAudioSource = JSDashManifestRawAudioSource(plugin, obj);
        fun fromV8HLSNullable(plugin: JSClient, obj: V8Value?) : JSHLSManifestSource? = obj.orNull { fromV8HLS(plugin, it as V8ValueObject) };
        fun fromV8HLS(plugin: JSClient, obj: V8ValueObject) : JSHLSManifestSource = JSHLSManifestSource(plugin, obj);

        fun fromV8Audio(plugin: JSClient, obj: V8ValueObject) : IAudioSource? {
            val type = obj.getString("plugin_type");
            return when(type) {
                TYPE_HLS -> JSHLSManifestAudioSource.fromV8HLS(plugin, obj);
                TYPE_AUDIOURL -> JSAudioUrlSource(plugin, obj);
                TYPE_DASH_RAW_AUDIO -> fromV8DashRawAudio(plugin, obj);
                TYPE_AUDIOURL_WIDEVINE -> JSAudioUrlWidevineSource(plugin, obj);
                TYPE_AUDIO_WITH_METADATA -> JSAudioUrlRangeSource(plugin, obj);
                else -> {
                    Logger.w("JSSource", "Unknown audio type ${type}");
                    null;
                };
            }
        }
    }
}