package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.playback.IPlaybackTracker
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.exceptions.ScriptImplementationException
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.warnIfMainThread

class JSPlaybackTracker: IPlaybackTracker {
    private val _config: IV8PluginConfig;
    private val _obj: V8ValueObject;

    private var _hasCalledInit: Boolean = false;
    private val _hasInit: Boolean;

    private var _lastRequest: Long = Long.MIN_VALUE;

    private val _hasOnConcluded: Boolean;

    override var nextRequest: Int = 1000
        private set;

    constructor(config: IV8PluginConfig, obj: V8ValueObject) {
        warnIfMainThread("JSPlaybackTracker.constructor");
        if(!obj.has("onProgress"))
            throw ScriptImplementationException(config, "Missing onProgress on PlaybackTracker");
        if(!obj.has("nextRequest"))
            throw ScriptImplementationException(config, "Missing nextRequest on PlaybackTracker");
        _hasOnConcluded = obj.has("onConcluded");

        this._config = config;
        this._obj = obj;
        this._hasInit = obj.has("onInit");
    }

    override fun onInit(seconds: Double) {
        warnIfMainThread("JSPlaybackTracker.onInit");
        synchronized(_obj) {
            if(_hasCalledInit)
                return;
            if (_hasInit) {
                Logger.i("JSPlaybackTracker", "onInit (${seconds})");
                _obj.invokeVoid("onInit", seconds);
            }
            nextRequest = Math.max(100, _obj.getOrThrow(_config, "nextRequest", "PlaybackTracker", false));
            _hasCalledInit = true;
        }
    }

    override fun onProgress(seconds: Double, isPlaying: Boolean) {
        warnIfMainThread("JSPlaybackTracker.onProgress");
        synchronized(_obj) {
            if(!_hasCalledInit && _hasInit)
                onInit(seconds);
            else {
                Logger.i("JSPlaybackTracker", "onProgress (${seconds}, ${isPlaying})");
                _obj.invokeVoid("onProgress", Math.floor(seconds), isPlaying);
                nextRequest = Math.max(100, _obj.getOrThrow(_config, "nextRequest", "PlaybackTracker", false));
                _lastRequest = System.currentTimeMillis();
            }
        }
    }
    override fun onConcluded() {
        warnIfMainThread("JSPlaybackTracker.onConcluded");
        if(_hasOnConcluded) {
            synchronized(_obj) {
                Logger.i("JSPlaybackTracker", "onConcluded");
                _obj.invokeVoid("onConcluded", -1);
            }
        }
    }


    override fun shouldUpdate(): Boolean = (_lastRequest < 0 || (System.currentTimeMillis() - _lastRequest) > nextRequest);
}