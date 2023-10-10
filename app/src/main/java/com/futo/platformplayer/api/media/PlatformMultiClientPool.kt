package com.futo.platformplayer.api.media

class PlatformMultiClientPool {
    private val _maxCap: Int;
    private val _clientPools: HashMap<IPlatformClient, PlatformClientPool> = hashMapOf();

    constructor(maxCap: Int = -1) {
        _maxCap = if(maxCap > 0)
            maxCap
        else 99;
    }

    fun getClientPooled(parentClient: IPlatformClient, capacity: Int): IPlatformClient {
        val pool = synchronized(_clientPools) {
            if(!_clientPools.containsKey(parentClient))
                _clientPools[parentClient] = PlatformClientPool(parentClient).apply {
                    this.onDead.subscribe { client, pool ->
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
}