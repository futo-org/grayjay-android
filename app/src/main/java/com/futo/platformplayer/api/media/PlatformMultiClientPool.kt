package com.futo.platformplayer.api.media

class PlatformMultiClientPool {
    private val _name: String;
    private val _maxCap: Int;
    private val _clientPools: HashMap<IPlatformClient, PlatformClientPool> = hashMapOf();

    private var _isFake = false;
    private var _privatePool = false;

    constructor(name: String, maxCap: Int = -1, isPrivatePool: Boolean = false) {
        _name = name;
        _maxCap = if(maxCap > 0)
            maxCap
        else 99;
        _privatePool = isPrivatePool;
    }

    fun getClientPooled(parentClient: IPlatformClient, capacity: Int = _maxCap): IPlatformClient {
        if(_isFake)
            return parentClient;
        val pool = synchronized(_clientPools) {
            if(!_clientPools.containsKey(parentClient))
                _clientPools[parentClient] = PlatformClientPool(parentClient, _name, _privatePool).apply {
                    this.onDead.subscribe { _, pool ->
                        synchronized(_clientPools) {
                            if(_clientPools[parentClient] == pool)
                                _clientPools.remove(parentClient);
                        }
                    }
                }
            _clientPools[parentClient]!!;
        };
        return pool.getClient(capacity.coerceAtMost(_maxCap));
    }

    //Allows for testing disabling pooling without changing callers
    fun asFake(): PlatformMultiClientPool {
        _isFake = true;
        return this;
    }
}