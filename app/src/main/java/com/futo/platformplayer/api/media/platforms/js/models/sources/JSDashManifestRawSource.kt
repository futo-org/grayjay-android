package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.primitive.V8ValueString
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.V8Deferred
import com.futo.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.futo.platformplayer.api.media.models.streams.sources.other.IStreamMetaDataSource
import com.futo.platformplayer.api.media.models.streams.sources.other.StreamMetaData
import com.futo.platformplayer.api.media.platforms.js.DevJSClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrNull
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.invokeV8
import com.futo.platformplayer.invokeV8Async
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateDeveloper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

interface IJSDashManifestRawSource {
    val hasGenerate: Boolean;
    var manifest: String?;
    fun generateAsync(scope: CoroutineScope): Deferred<String?>;
    fun generate(): String?;
}
open class JSDashManifestRawSource: JSSource, IVideoSource, IJSDashManifestRawSource, IStreamMetaDataSource {
    override val container : String;
    override val name : String;
    override val width: Int;
    override val height: Int;
    override val codec: String;
    override val bitrate: Int?;
    override val duration: Long;
    override val priority: Boolean;

    val url: String?;
    override var manifest: String?;

    override val hasGenerate: Boolean;
    val canMerge: Boolean;

    override var streamMetaData: StreamMetaData? = null;

    constructor(plugin: JSClient, obj: V8ValueObject) : super(TYPE_DASH_RAW, plugin, obj) {
        val contextName = "DashRawSource";
        val config = plugin.config;
        name = _obj.getOrThrow(config, "name", contextName);
        url = _obj.getOrThrow(config, "url", contextName);
        container = _obj.getOrDefault<String>(config, "container", contextName, null) ?: "application/dash+xml";
        manifest = _obj.getOrDefault<String>(config, "manifest", contextName, null);
        width = _obj.getOrDefault(config, "width", contextName, 0) ?: 0;
        height = _obj.getOrDefault(config, "height", contextName, 0) ?: 0;
        codec = _obj.getOrDefault(config, "codec", contextName, "") ?: "";
        bitrate = _obj.getOrDefault(config, "bitrate", contextName, 0) ?: 0;
        duration = _obj.getOrDefault(config, "duration", contextName, 0) ?: 0;
        priority = _obj.getOrDefault(config, "priority", contextName, false) ?: false;
        canMerge = _obj.getOrDefault(config, "canMerge", contextName, false) ?: false;
        hasGenerate = _obj.has("generate");
    }

    private var _pregenerate: V8Deferred<String?>? = null;
    fun pregenerateAsync(scope: CoroutineScope): V8Deferred<String?>? {
        _pregenerate = generateAsync(scope);
        return _pregenerate;
    }

    override fun generateAsync(scope: CoroutineScope): V8Deferred<String?> {
        if(!hasGenerate)
            return V8Deferred(CompletableDeferred(manifest));
        if(_obj.isClosed)
            throw IllegalStateException("Source object already closed");
        val pregenerated = _pregenerate;
        if(pregenerated != null) {
            Logger.w("JSDashManifestRawSource", "Returning pre-generated video");
            return pregenerated;
        }

        val plugin = _plugin.getUnderlyingPlugin();

        var result: V8Deferred<V8ValueString>? = null;
        if(_plugin is DevJSClient) {
            result = StateDeveloper.instance.handleDevCall(_plugin.devID, "DashManifestRawSource.generate()") {
                _plugin.getUnderlyingPlugin().catchScriptErrors("DashManifestRaw.generate", "generate()", {
                    _plugin.isBusyWith("dashVideo.generate") {
                        _obj.invokeV8Async<V8ValueString>("generate");
                    }
                });
            }
        }
        else
            result = _plugin.getUnderlyingPlugin().catchScriptErrors("DashManifestRaw.generate", "generate()", {
                _plugin.isBusyWith("dashVideo.generate") {
                    _obj.invokeV8Async<V8ValueString>("generate");
                }
            });

        return plugin.busy {
            val initStart = _obj.getOrDefault<Int>(_config, "initStart", "JSDashManifestRawSource", null) ?: 0;
            val initEnd = _obj.getOrDefault<Int>(_config, "initEnd", "JSDashManifestRawSource", null) ?: 0;
            val indexStart = _obj.getOrDefault<Int>(_config, "indexStart", "JSDashManifestRawSource", null) ?: 0;
            val indexEnd = _obj.getOrDefault<Int>(_config, "indexEnd", "JSDashManifestRawSource", null) ?: 0;
            if(initEnd > 0 && indexStart > 0 && indexEnd > 0) {
                streamMetaData = StreamMetaData(initStart, initEnd, indexStart, indexEnd);
            }

            return@busy result.convert {
                it.value
            };
        }
    }
    override open fun generate(): String? {
        if(!hasGenerate)
            return manifest;
        if(_obj.isClosed)
            throw IllegalStateException("Source object already closed");

        var result: String? = null;
        if(_plugin is DevJSClient) {
            result = StateDeveloper.instance.handleDevCall(_plugin.devID, "DashManifestRawSource.generate()") {
                _plugin.getUnderlyingPlugin().catchScriptErrors("DashManifestRaw.generate", "generate()", {
                    _plugin.isBusyWith("dashVideo.generate") {
                        _obj.invokeV8<V8ValueString>("generate").value;
                    }
                });
            }
        }
        else
            result = _plugin.getUnderlyingPlugin().catchScriptErrors("DashManifestRaw.generate", "generate()", {
                _plugin.isBusyWith("dashVideo.generate") {
                    _obj.invokeV8<V8ValueString>("generate").value;
                }
            });

        if(result != null){
            _plugin.busy {
                val initStart = _obj.getOrDefault<Int>(_config, "initStart", "JSDashManifestRawSource", null) ?: 0;
                val initEnd = _obj.getOrDefault<Int>(_config, "initEnd", "JSDashManifestRawSource", null) ?: 0;
                val indexStart = _obj.getOrDefault<Int>(_config, "indexStart", "JSDashManifestRawSource", null) ?: 0;
                val indexEnd = _obj.getOrDefault<Int>(_config, "indexEnd", "JSDashManifestRawSource", null) ?: 0;
                if(initEnd > 0 && indexStart > 0 && indexEnd > 0) {
                    streamMetaData = StreamMetaData(initStart, initEnd, indexStart, indexEnd);
                }
            }
        }
        return result;
    }
}

class JSDashManifestMergingRawSource(
    val video: JSDashManifestRawSource,
    val audio: JSDashManifestRawAudioSource): JSDashManifestRawSource(video.getUnderlyingPlugin()!!, video.getUnderlyingObject()!!), IVideoSource {

    override val name: String
        get() = video.name;
    override val bitrate: Int
        get() = (video.bitrate ?: 0) + audio.bitrate;
    override val codec: String
        get() = video.codec
    override val container: String
        get() = video.container
    override val duration: Long
        get() = video.duration;
    override val height: Int
        get() = video.height;
    override val width: Int
        get() = video.width;
    override val priority: Boolean
        get() = video.priority;

    override fun generateAsync(scope: CoroutineScope): V8Deferred<String?> {
        val videoDashDef = video.generateAsync(scope);
        val audioDashDef = audio.generateAsync(scope);

        return V8Deferred.merge(scope, listOf(videoDashDef, audioDashDef)) {
            val (videoDash: String?, audioDash: String?) = it;

            if (videoDash != null && audioDash == null) return@merge videoDash;
            if (audioDash != null && videoDash == null) return@merge audioDash;
            if (videoDash == null) return@merge null;

            //TODO: Temporary simple solution..make more reliable version

            var result: String? = null;
            val audioAdaptationSet = adaptationSetRegex.find(audioDash!!);
            if (audioAdaptationSet != null) {
                result = videoDash.replace(
                    "</AdaptationSet>",
                    "</AdaptationSet>\n" + audioAdaptationSet.value
                )
            } else
                result = videoDash;

            return@merge result;
        };
    }
    override fun generate(): String? {
        val videoDash = video.generate();
        val audioDash = audio.generate();
        if(videoDash != null && audioDash == null) return videoDash;
        if(audioDash != null && videoDash == null) return audioDash;
        if(videoDash == null) return null;

        //TODO: Temporary simple solution..make more reliable version

        var result: String? = null;
        val audioAdaptationSet = adaptationSetRegex.find(audioDash!!);
        if(audioAdaptationSet != null) {
            result = videoDash.replace("</AdaptationSet>", "</AdaptationSet>\n" + audioAdaptationSet.value)
        }
        else
            result = videoDash;

        return result;
    }

    companion object {
        private val adaptationSetRegex = Regex("<AdaptationSet.*?>.*?<\\/AdaptationSet>", RegexOption.DOT_MATCHES_ALL);
    }
}