package com.futo.platformplayer.tv.install

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import kotlin.coroutines.resume

object TvAdbDiscovery {
    suspend fun connectionPort(context: Context, host: String): Int? = withContext(Dispatchers.IO) {
        val address = InetAddress.getByName(host.trim().removeSurrounding("[", "]"))
        val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        var discovery: NsdManager.DiscoveryListener? = null
        try {
            withTimeoutOrNull(6000) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : NsdManager.DiscoveryListener {
                        override fun onDiscoveryStarted(type: String) {}
                        override fun onDiscoveryStopped(type: String) {}
                        override fun onServiceLost(service: NsdServiceInfo) {}
                        override fun onStopDiscoveryFailed(type: String, code: Int) {}
                        override fun onStartDiscoveryFailed(type: String, code: Int) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                        override fun onServiceFound(service: NsdServiceInfo) {
                            if (!continuation.isActive) return
                            @Suppress("DEPRECATION")
                            manager.resolveService(service, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(info: NsdServiceInfo, code: Int) {}
                                override fun onServiceResolved(info: NsdServiceInfo) {
                                    @Suppress("DEPRECATION")
                                    if (info.host == address && info.port in 1..65535 && continuation.isActive)
                                        continuation.resume(info.port)
                                }
                            })
                        }
                    }
                    discovery = listener
                    manager.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
                }
            }
        } finally {
            discovery?.let { runCatching { manager.stopServiceDiscovery(it) } }
        }
    }
}
