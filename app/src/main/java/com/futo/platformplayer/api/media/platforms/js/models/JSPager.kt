package com.futo.platformplayer.api.media.platforms.js.models

import android.os.Looper
import com.caoccao.javet.values.reference.V8ValueArray
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.getSourcePlugin
import com.futo.platformplayer.invokeV8
import com.futo.platformplayer.warnIfMainThread

abstract class JSPager<T> : IPager<T> {
    protected val plugin: JSClient;
    protected val config: SourcePluginConfig;
    protected var pager: V8ValueObject;

    private var _lastResults: List<T>? = null;
    protected var _resultChanged: Boolean = true;
    protected var _hasMorePages: Boolean = false;
    //private var _morePagesWasFalse: Boolean = false;

    val isAvailable get() = pager.v8Runtime?.let { !it.isClosed && !it.isDead } ?: false;

    constructor(config: SourcePluginConfig, plugin: JSClient, pager: V8ValueObject) {
        this.plugin = plugin;
        this.pager = pager;
        this.config = config;

        requirePagerPluginV8("init").busy {
            _hasMorePages = pager.getOrDefault(config, "hasMore", "Pager", false) ?: false;
        }
        getResults();
    }

    fun getPluginConfig(): SourcePluginConfig {
        return config;
    }

    protected fun requirePagerPluginV8(context: String): V8Plugin {
        return pager.getSourcePlugin()
            ?: throw IllegalStateException("[${plugin.config.name}] JSPager.$context: pager runtime is no longer available");
    }

    override fun hasMorePages(): Boolean {
        val pluginV8 = pager.getSourcePlugin() ?: return false;
        return pluginV8.busy {
            _hasMorePages && !pager.isClosed;
        }
    }

    override fun nextPage() {
        warnIfMainThread("JSPager.nextPage");

        val pluginV8 = requirePagerPluginV8("nextPage");
        pluginV8.busy {
            pager = pluginV8.catchScriptErrors("[${plugin.config.name}] JSPager", "pager.nextPage()") {
                pager.invokeV8("nextPage", arrayOf<Any>());
            };
            _hasMorePages = pager.getOrDefault(config, "hasMore", "Pager", false) ?: false;
            _resultChanged = true;
        }
        /*
        try {
        }
        catch(ex: Throwable) {
            Logger.e("JSPager", "[${plugin.config.name}] Failed to load next page", ex);
            _lastResults = listOf();
            UIDialogs.toast("Failed to get more results for plugin [${plugin.config.name}]\n${ex.message}");
        }*/
    }

    override fun getResults(): List<T> {
        val previousResults = _lastResults?.let {
            if(!_resultChanged)
                return@let it;
            else
                null;
        };
        if(previousResults != null)
            return previousResults;

        warnIfMainThread("JSPager.getResults");

        return requirePagerPluginV8("getResults").busy {
            val items = pager.getOrThrow<V8ValueArray>(config, "results", "JSPager");
            if (items.v8Runtime.isDead || items.v8Runtime.isClosed)
                throw IllegalStateException("Runtime closed");
            val newResults = items.toArray()
                .map { convertResult(it as V8ValueObject) }
                .toList();
            _lastResults = newResults;
            _resultChanged = false;
            return@busy newResults;
        }
    }

    abstract fun convertResult(obj: V8ValueObject): T;
}
