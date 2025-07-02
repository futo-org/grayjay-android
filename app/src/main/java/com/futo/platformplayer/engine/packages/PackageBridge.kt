package com.futo.platformplayer.engine.packages

import android.media.MediaCodec
import android.media.MediaCodecList
import com.caoccao.javet.annotations.V8Function
import com.caoccao.javet.annotations.V8Property
import com.caoccao.javet.interop.callback.JavetCallbackContext
import com.caoccao.javet.utils.JavetResourceUtils
import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.reference.V8ValueFunction
import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateDeveloper
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.models.contents.ContentType
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.JSClientConstants
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.platforms.js.internal.JSHttpClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.states.StateApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class PackageBridge : V8Package {
    @Transient
    private val _config: IV8PluginConfig;
    @Transient
    private val _client: ManagedHttpClient
    @Transient
    private val _clientAuth: ManagedHttpClient


    override val name: String get() = "Bridge";
    override val variableName: String get() = "bridge";

    constructor(plugin: V8Plugin, config: IV8PluginConfig): super(plugin) {
        _config = config;
        _client = plugin.httpClient;
        _clientAuth = plugin.httpClientAuth;

        withScript("""
             function setTimeout(func, delay) {
                 let args = Array.prototype.slice.call(arguments, 2);
                 return bridge.setTimeout(func.bind(globalThis, ...args), delay || 0);
             }
        """.trimIndent());
        withScript("""
               function clearTimeout(id) {
                    bridge.clearTimeout(id);
                }
        """.trimIndent());
    }


    @V8Property
    fun buildVersion(): Int {
        //If debug build, assume max version
        if(BuildConfig.VERSION_CODE == 1)
            return Int.MAX_VALUE;
        return BuildConfig.VERSION_CODE;
    }
    @V8Property
    fun buildFlavor(): String {
        return BuildConfig.FLAVOR;
    }
    @V8Property
    fun buildSpecVersion(): Int {
        return JSClientConstants.PLUGIN_SPEC_VERSION;
    }
    @V8Property
    fun buildPlatform(): String {
        return "android";
    }

    @V8Property
    fun supportedFeatures(): Array<String> {
        return arrayOf(
            "ReloadRequiredException",
            "HttpBatchClient",
            "Async"
        );
    }

    @V8Property
    fun supportedContent(): Array<Int> {
        return arrayOf(
            ContentType.MEDIA.value,
            ContentType.POST.value,
            ContentType.PLAYLIST.value,
            ContentType.WEB.value,
            ContentType.URL.value,
            ContentType.NESTED_VIDEO.value,
            ContentType.CHANNEL.value,
            ContentType.LOCKED.value,
            ContentType.PLACEHOLDER.value,
            ContentType.DEFERRED.value
        )
    }

    @V8Function
    fun dispose(value: V8Value) {
        Logger.e(TAG, "Manual dispose: " + value.javaClass.name);
        value.close();
    }

    var timeoutCounter = 0;
    var timeoutMap = ConcurrentHashMap<Int, Any?>();
    @V8Function
    fun setTimeout(func: V8ValueFunction, timeout: Long): Int {
        val id = timeoutCounter++;
        val funcClone = func.toClone<V8ValueFunction>()

        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
            delay(timeout);
            if (_plugin.isStopped)
                return@launch;
            if (!timeoutMap.containsKey(id)) {
                _plugin.busy {
                    if (!_plugin.isStopped)
                        JavetResourceUtils.safeClose(funcClone);
                }
                return@launch;
            }
            timeoutMap.remove(id);
            try {
                Logger.w(TAG, "setTimeout before busy (${timeout}): ${_plugin.isBusy}");
                _plugin.busy {
                    Logger.w(TAG, "setTimeout in busy");
                    if (!_plugin.isStopped)
                        funcClone.callVoid(null, arrayOf<Any>());
                    Logger.w(TAG, "setTimeout after");
                }
            } catch (ex: Throwable) {
                Logger.e(TAG, "Failed timeout callback", ex);
            } finally {
                _plugin.busy {
                    if (!_plugin.isStopped)
                        JavetResourceUtils.safeClose(funcClone);
                }
                //_plugin.whenNotBusy {
                //}
            }
        };
        timeoutMap.put(id, true);
        return id;
    }
    @V8Function
    fun clearTimeout(id: Int) {
        if (timeoutMap.containsKey(id))
            timeoutMap.remove(id);
    }
    @V8Function
    fun sleep(length: Int) {
        Thread.sleep(length.toLong());
    }

    @V8Function
    fun toast(str: String) {
        Logger.i(TAG, "Plugin toast [${_config.name}]: ${str}");
        StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
            try {
                UIDialogs.appToast(str);
            } catch (e: Throwable) {
                Logger.e(TAG, "Failed to show toast.", e);
            }
        }
    }
    @V8Function
    fun log(str: String?) {
        Logger.i(_config.name, str ?: "null");
        if(_config is SourcePluginConfig && _config.id == StateDeveloper.DEV_ID)
            StateDeveloper.instance.logDevInfo(StateDeveloper.instance.currentDevID ?: "", str ?: "null");
    }

    private val _jsonSerializer = Json { this.prettyPrintIndent = "   "; this.prettyPrint = true; };
    private var _devSubmitClient: ManagedHttpClient? = null;
    @V8Function
    fun devSubmit(label: String, data: String) {
        if(_plugin.config !is SourcePluginConfig)
            return;
        if(!_plugin.allowDevSubmit)
            return;
        val devUrl = _plugin.config.developerSubmitUrl ?: return;
        if(_devSubmitClient == null)
            _devSubmitClient = ManagedHttpClient();

        val stackTrace = Thread.currentThread().stackTrace;
        val callerMethod = stackTrace.findLast {
            it.className == JSClient::class.java.name
        }?.methodName ?: "";
        val session = StateApp.instance.sessionId;
        val pluginId = _plugin.config.id;
        val pluginVersion = _plugin.config.version;

        val obj = DevSubmitData(pluginId, pluginVersion, callerMethod, session, label, data);

        UIDialogs.toast("DevSubmit [${callerMethod}] (${_plugin.config.name})", false);
        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
            try {
                val json = _jsonSerializer.encodeToString(obj);
                Logger.i(TAG, "DevSubmit [${callerMethod}] - ${devUrl}\n" + json);
                val resp = _devSubmitClient?.post(devUrl, json, mutableMapOf(Pair("Content-Type", "application/json")));
                Logger.i(TAG, "DevSubmit [${callerMethod}] - ${devUrl} Status: " + (resp?.code?.toString() ?: "-1"))
            }
            catch(ex: Exception) {
                Logger.e(TAG, "DevSubmission to [${devUrl}] failed due to:\n" + ex.message, ex);
            }
        }
    }
    @Serializable
    class DevSubmitData(val pluginId: String, val pluginVersion: Int, val caller: String, val session: String, val label: String, val data: String)

    @V8Function
    fun throwTest(str: String) {
        throw IllegalStateException(str);
    }

    @V8Function
    fun isLoggedIn(): Boolean {
        if (_clientAuth is JSHttpClient)
            return _clientAuth.isLoggedIn;
        return false;
    }

    @V8Function
    fun getHardwareCodecs(): List<String>{
        return getSupportedHardwareMediaCodecs();
    }


    companion object {
        private const val TAG = "PackageBridge";

        private var _mediaCodecList: MutableList<String> = mutableListOf();
        private var _mediaCodecListHardware: MutableList<String> = mutableListOf();

        fun getSupportedMediaCodecs(): List<String>{
            synchronized(_mediaCodecList) {
                if(_mediaCodecList.size <= 0)
                    updateMediaCodecList();
                return _mediaCodecList;
            }
        }
        fun getSupportedHardwareMediaCodecs(): List<String>{
            synchronized(_mediaCodecList) {
                if(_mediaCodecList.size <= 0)
                    updateMediaCodecList();
                return _mediaCodecListHardware;
            }
        }
        private fun updateMediaCodecList() {
            _mediaCodecList.clear();
            _mediaCodecListHardware.clear();
            for(codec in MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos) {
                if(!codec.isEncoder) {
                    _mediaCodecList.add(codec.canonicalName);
                    if (codec.isHardwareAccelerated)
                        _mediaCodecListHardware.add(codec.canonicalName);
                }
            }
        }


    }
}