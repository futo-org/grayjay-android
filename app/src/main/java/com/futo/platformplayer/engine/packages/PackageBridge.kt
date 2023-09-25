package com.futo.platformplayer.engine.packages

import com.caoccao.javet.annotations.V8Function
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateDeveloper
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.platforms.js.internal.JSHttpClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.states.StateApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    }

    @V8Function
    fun toast(str: String) {
        StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
            try {
                UIDialogs.toast(str);
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

    companion object {
        private const val TAG = "PackageBridge";
    }
}