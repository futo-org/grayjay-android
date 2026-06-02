package com.futo.platformplayer.api.media

import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp

class PlatformClientPool {
    private val _parent: JSClient;
    private val _pool: HashMap<JSClient, Int> = hashMapOf();
    private var _poolCounter = 0;
    private var _inFlight = 0;
    private val _poolName: String?;
    private val _privatePool: Boolean;
    private val _isolatedInitialization: Boolean

    @Volatile
    var isDead: Boolean = false
        private set;
    val onDead = Event2<JSClient, PlatformClientPool>();

    constructor(parentClient: IPlatformClient, name: String? = null, privatePool: Boolean = false, isolatedInitialization: Boolean = false) {
        _poolName = name;
        _privatePool = privatePool;
        _isolatedInitialization = isolatedInitialization
        if(parentClient !is JSClient)
            throw IllegalArgumentException("Pooling only supported for JSClients right now");
        Logger.i(TAG, "Pool for ${parentClient.name} was started");

        this._parent = parentClient;
        parentClient.getUnderlyingPlugin().onStopped.subscribe {
            Logger.i(TAG, "Pool for [${parentClient.name}] was killed");
            isDead = true;
            onDead.emit(parentClient, this);

            val toDisable = synchronized(_pool) { _pool.keys.toList() };
            for (client in toDisable) {
                try { client.disable(); }
                catch (ex: Throwable) { Logger.w(TAG, "Failed to disable pooled client", ex); }
            }
        };
    }

    fun getClient(capacity: Int): IPlatformClient {
        if(capacity < 1)
            throw IllegalArgumentException("Capacity should be at least 1");
        val parentPlugin = _parent.getUnderlyingPlugin();
        if(parentPlugin._runtime?.isDead == true || parentPlugin._runtime?.isClosed == true) {
            isDead = true;
            onDead.emit(_parent, this);
        }

        synchronized(_pool) {
            val free = _pool.keys.firstOrNull { !it.isBusy };
            if(free != null) {
                _poolCounter++;
                _pool[free] = _poolCounter;
                return free;
            }
            if(_pool.size + _inFlight < capacity || _pool.isEmpty()) {
                _inFlight++;
            }
            else {
                _poolCounter++;
                val lru = _pool.entries.toList().sortedBy { it.value }.first().key;
                _pool[lru] = _poolCounter;
                return lru;
            }
        }

        val created: JSClient;
        var pending: JSClient? = null;
        try {
            Logger.i(TAG, "Started additional [${_parent.name}] client in pool [${_poolName}]");
            val client = _parent.getCopy(_privatePool, _isolatedInitialization);
            pending = client;
            client.onCaptchaException.subscribe { c, ex ->
                StateApp.instance.handleCaptchaException(c, ex);
            };
            client.initialize();
            created = client;
        }
        catch (ex: Throwable) {
            synchronized(_pool) { _inFlight--; }
            try { pending?.disable(); } catch (_: Throwable) {}
            throw ex;
        }

        synchronized(_pool) {
            _inFlight--;
            if(isDead) {
                try { created.disable(); } catch (_: Throwable) {}
                throw IllegalStateException("Pool for [${_parent.name}] died during client creation");
            }
            _poolCounter++;
            _pool[created] = _poolCounter;
            return created;
        }
    }


    companion object {
        val TAG = "PlatformClientPool";
    }
}