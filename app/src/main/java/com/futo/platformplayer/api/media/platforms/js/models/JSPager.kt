package com.futo.platformplayer.api.media.platforms.js.models

import android.os.Looper
import com.caoccao.javet.values.reference.V8ValueArray
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.BuildConfig
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.warnIfMainThread

abstract class JSPager<T> : IPager<T> {
    protected val plugin: V8Plugin;
    protected val config: SourcePluginConfig;
    protected var pager: V8ValueObject;

    private var _lastResults: List<T>? = null;
    private var _resultChanged: Boolean = true;
    private var _hasMorePages: Boolean = false;
    //private var _morePagesWasFalse: Boolean = false;

    val isAvailable get() = plugin._runtime?.let { !it.isClosed && !it.isDead } ?: false;

    constructor(config: SourcePluginConfig, plugin: V8Plugin, pager: V8ValueObject) {
        this.plugin = plugin;
        this.pager = pager;
        this.config = config;

        _hasMorePages = pager.getOrThrow(config, "hasMore", "Pager");
        getResults();
    }

    fun getPluginConfig(): SourcePluginConfig {
        return config;
    }

    override fun hasMorePages(): Boolean {
        return _hasMorePages;
    }

    override fun nextPage() {
        warnIfMainThread("JSPager.nextPage");

        pager = plugin.catchScriptErrors("[${plugin.config.name}] JSPager", "pager.nextPage()") {
            pager.invoke("nextPage", arrayOf<Any>());
        };
        _hasMorePages = pager.getOrThrow(config, "hasMore", "Pager");
        _resultChanged = true;
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
        warnIfMainThread("JSPager.getResults");

        val previousResults = _lastResults?.let {
            if(!_resultChanged)
                return@let it;
            else
                null;
        };
        if(previousResults != null)
            return previousResults;

        val items = pager.getOrThrow<V8ValueArray>(config, "results", "JSPager");
        val newResults = items.toArray()
            .map { convertResult(it as V8ValueObject) }
            .toList();
        _lastResults = newResults;
        _resultChanged = false;
        return newResults;
    }

    abstract fun convertResult(obj: V8ValueObject): T;
}