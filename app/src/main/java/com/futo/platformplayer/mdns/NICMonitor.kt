package com.futo.platformplayer.mdns

import kotlinx.coroutines.*
import java.net.NetworkInterface

class NICMonitor {
    private val lockObject = Any()
    private val nics = mutableListOf<NetworkInterface>()
    private var cts: Job? = null

    val current: List<NetworkInterface>
        get() = synchronized(nics) { nics.toList() }

    var added: ((List<NetworkInterface>) -> Unit)? = null
    var removed: ((List<NetworkInterface>) -> Unit)? = null

    fun start() {
        synchronized(lockObject) {
            if (cts != null) throw Exception("Already started.")

            cts = CoroutineScope(Dispatchers.Default).launch {
                loopAsync()
            }
        }

        nics.clear()
        nics.addAll(getCurrentInterfaces().toList())
    }

    fun stop() {
        synchronized(lockObject) {
            cts?.cancel()
            cts = null
        }

        synchronized(nics) {
            nics.clear()
        }
    }

    private suspend fun loopAsync() {
        while (cts?.isActive == true) {
            try {
                val currentNics = getCurrentInterfaces().toList()
                removed?.invoke(nics.filter { k -> currentNics.none { n -> k.name == n.name } })
                added?.invoke(currentNics.filter { nic -> nics.none { k -> k.name == nic.name } })

                synchronized(nics) {
                    nics.clear()
                    nics.addAll(currentNics)
                }
            } catch (ex: Exception) {
                // Ignored
            }
            delay(5000)
        }
    }

    private fun getCurrentInterfaces(): List<NetworkInterface> {
        val nics = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }

        return if (nics.isNotEmpty()) nics else NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp }
    }
}
