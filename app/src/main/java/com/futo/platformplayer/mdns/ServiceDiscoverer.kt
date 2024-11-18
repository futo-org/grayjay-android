package com.futo.platformplayer.mdns

import com.futo.platformplayer.logging.Logger
import java.lang.Thread.sleep

class ServiceDiscoverer(names: Array<String>, private val _onServicesUpdated: (List<DnsService>) -> Unit) {
    private val _names: Array<String>
    private var _listener: MDNSListener? = null
    private var _started = false
    private var _thread: Thread? = null

    init {
        if (names.isEmpty()) throw IllegalArgumentException("At least one name must be specified.")
        _names = names
    }

    fun broadcastService(
        deviceName: String,
        serviceName: String,
        port: UShort,
        ttl: UInt = 120u,
        weight: UShort = 0u,
        priority: UShort = 0u,
        texts: List<String>? = null
    ) {
        _listener?.let {
            it.broadcastService(deviceName, serviceName, port, ttl, weight, priority, texts)
        }
    }

    fun stop() {
        _started = false
        _listener?.stop()
        _listener = null
        _thread?.join()
        _thread = null
    }

    fun start() {
        if (_started) {
            Logger.i(TAG, "Already started.")
            return
        }
        _started = true

        val listener = MDNSListener()
        _listener = listener
        listener.onServicesUpdated = { _onServicesUpdated?.invoke(it) }
        listener.start()

        _thread = Thread {
            try {
                sleep(2000)

                while (_started) {
                    listener.queryServices(_names)
                    sleep(2000)
                    listener.queryAllQuestions(_names)
                    sleep(2000)
                }
            } catch (e: Throwable) {
                Logger.i(TAG, "Exception in loop thread", e)
                stop()
            }
        }.apply { start() }
    }

    companion object {
        private val TAG = "ServiceDiscoverer"
    }
}
