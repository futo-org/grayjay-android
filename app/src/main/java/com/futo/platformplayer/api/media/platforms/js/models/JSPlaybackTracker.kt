package com.futo.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.playback.IPlaybackTracker
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.exceptions.ScriptImplementationException
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.invokeV8Void
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.warnIfMainThread

class JSPlaybackTracker: IPlaybackTracker {
    private lateinit var _client: JSClient;
    private lateinit var _config: IV8PluginConfig;
    private lateinit var _obj: V8ValueObject;

    private var _hasCalledInit: Boolean = false;
    private var _hasInit: Boolean = false;

    private var _lastRequest: Long = Long.MIN_VALUE;

    private var _hasOnConcluded: Boolean = false;

    override var nextRequest: Int = 1000
        private set;

    constructor(client: JSClient, obj: V8ValueObject) {
        warnIfMainThread("JSPlaybackTracker.constructor");

        client.busy {
            if (!obj.has("onProgress"))
                throw ScriptImplementationException(
                    client.config,
                    "Missing onProgress on PlaybackTracker"
                );
            if (!obj.has("nextRequest"))
                throw ScriptImplementationException(
                    client.config,
                    "Missing nextRequest on PlaybackTracker"
                );
            _hasOnConcluded = obj.has("onConcluded");

            this._client = client;
            this._config = client.config;
            this._obj = obj;
            this._hasInit = obj.has("onInit");
        }
    }

    override fun onInit(seconds: Double) {
        warnIfMainThread("JSPlaybackTracker.onInit");
        synchronized(_obj) {
            if(_hasCalledInit)
                return;

            _client.busy {
                if (_hasInit) {
                    Logger.i("JSPlaybackTracker", "onInit (${seconds})");
                    _obj.invokeV8Void("onInit", seconds);
                }
                nextRequest = Math.max(100, _obj.getOrThrow(_config, "nextRequest", "PlaybackTracker", false));
                _hasCalledInit = true;
            }
        }
    }

    override fun onProgress(seconds: Double, isPlaying: Boolean) {
        warnIfMainThread("JSPlaybackTracker.onProgress");
        synchronized(_obj) {
            if(!_hasCalledInit && _hasInit)
                onInit(seconds);
            else {
                _client.busy {
                    Logger.i("JSPlaybackTracker", "onProgress (${seconds}, ${isPlaying})");
                    _obj.invokeV8Void("onProgress", Math.floor(seconds), isPlaying);
                    nextRequest = Math.max(100, _obj.getOrThrow(_config, "nextRequest", "PlaybackTracker", false));
                    _lastRequest = System.currentTimeMillis();
                }
            }
        }
    }
    override fun onConcluded() {
        warnIfMainThread("JSPlaybackTracker.onConcluded");
        if(_hasOnConcluded) {
            synchronized(_obj) {
                Logger.i("JSPlaybackTracker", "onConcluded");
                _client.busy {
                    _obj.invokeV8Void("onConcluded", -1);
                }
            }
        }
    }


    override fun shouldUpdate(): Boolean = (_lastRequest < 0 || (System.currentTimeMillis() - _lastRequest) > nextRequest);
}