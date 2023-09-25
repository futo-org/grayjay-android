package com.futo.platformplayer.api.media

import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.constructs.Event2
import com.futo.platformplayer.logging.Logger

class PlatformClientPool {
    private val _parent: JSClient;
    private val _pool: HashMap<JSClient, Int> = hashMapOf();
    private var _poolCounter = 0;

    var isDead: Boolean = false
        private set;
    val onDead = Event2<JSClient, PlatformClientPool>();

    constructor(parentClient: IPlatformClient) {
        if(parentClient !is JSClient)
            throw IllegalArgumentException("Pooling only supported for JSClients right now");
        Logger.i(TAG, "Pool for ${parentClient.name} was started");

        this._parent = parentClient;
        parentClient.getUnderlyingPlugin().onStopped.subscribe {
            Logger.i(TAG, "Pool for [${parentClient.name}] was killed");
            isDead = true;
            onDead.emit(parentClient, this);

            for(clientPair in _pool) {
                clientPair.key.disable();
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

        var reserved: JSClient?;
        synchronized(_pool) {
            _poolCounter++;
            reserved = _pool.keys.find { !it.isBusy };
            if(reserved == null && _pool.size < capacity) {
                Logger.i(TAG, "Started additional [${_parent.name}] client in pool (${_pool.size + 1}/${capacity})");
                reserved = _parent.getCopy();
                reserved?.initialize();
                _pool[reserved!!] = _poolCounter;
            }
            else
                reserved = _pool.entries.toList().sortedBy { it.value }.first().key;
            _pool[reserved!!] = _poolCounter;
        }
        return reserved!!;
    }


    companion object {
        val TAG = "PlatformClientPool";
    }
}