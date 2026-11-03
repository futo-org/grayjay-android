package com.futo.platformplayer.casting.dlna

import android.content.Context
import android.net.wifi.WifiManager
import com.futo.platformplayer.logging.Logger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds UPnP MediaRenderers on the local network using SSDP M-SEARCH and reports their parsed descriptions.
 */
class SsdpDiscoverer(
    private val context: Context,
    private val onRendererFound: (DlnaDescription) -> Unit
) {
    private val _client = DlnaSoap.createClient()
    private val _known = ConcurrentHashMap<String, DlnaDescription>()
    private val _resolving = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var _running = false
    private var _thread: Thread? = null
    private var _socket: DatagramSocket? = null
    private var _multicastLock: WifiManager.MulticastLock? = null

    @Synchronized
    fun start() {
        if (_running) return
        _running = true

        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            _multicastLock = wifi.createMulticastLock("grayjay-ssdp").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to acquire multicast lock: ${e.message}")
        }

        _thread = Thread({ run() }, "SsdpDiscoverer").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun stop() {
        _running = false
        _socket?.close()
        _socket = null
        _thread = null
        try {
            _multicastLock?.release()
        } catch (_: Throwable) { }
        _multicastLock = null
    }

    private fun run() {
        try {
            DatagramSocket().use { socket ->
                _socket = socket
                socket.soTimeout = 1000
                val group = InetAddress.getByName(SSDP_ADDRESS)
                val buffer = ByteArray(4096)
                var nextSearch = 0L

                while (_running) {
                    val now = System.currentTimeMillis()
                    if (now >= nextSearch) {
                        for (target in SEARCH_TARGETS) {
                            val msg = searchMessage(target).toByteArray()
                            socket.send(DatagramPacket(msg, msg.size, group, SSDP_PORT))
                        }
                        nextSearch = now + SEARCH_INTERVAL_MS
                    }

                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        handleResponse(String(packet.data, 0, packet.length, Charsets.UTF_8))
                    } catch (_: SocketTimeoutException) { }
                }
            }
        } catch (e: Throwable) {
            if (_running) {
                Logger.e(TAG, "SSDP discovery failed", e)
            }
        }
    }

    private fun handleResponse(response: String) {
        val headers = response.split("\r\n")
            .mapNotNull { line -> line.indexOf(':').takeIf { it > 0 }?.let { line.substring(0, it).trim().uppercase() to line.substring(it + 1).trim() } }
            .toMap()

        val st = headers["ST"] ?: headers["NT"] ?: return
        if (!st.contains("MediaRenderer", true) && !st.contains("AVTransport", true)) return
        val location = headers["LOCATION"] ?: return
        if (_known.containsKey(location) || !_resolving.add(location)) return

        Thread({
            try {
                val body = _client.get(location).use { if (it.isOk) it.body?.string() else null } ?: return@Thread
                val description = DlnaDescription.parse(location, body) ?: return@Thread
                _known[location] = description
                Logger.i(TAG, "Found DLNA renderer '${description.friendlyName}' at $location")
                onRendererFound(description)
            } catch (e: Throwable) {
                Logger.w(TAG, "Failed to resolve renderer at $location: ${e.message}")
            } finally {
                _resolving.remove(location)
            }
        }, "SsdpResolve").apply { isDaemon = true }.start()
    }

    private fun searchMessage(target: String) =
        "M-SEARCH * HTTP/1.1\r\n" +
        "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
        "MAN: \"ssdp:discover\"\r\n" +
        "MX: 2\r\n" +
        "ST: $target\r\n\r\n"

    companion object {
        private const val TAG = "SsdpDiscoverer"
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val SEARCH_INTERVAL_MS = 15_000L
        private val SEARCH_TARGETS = listOf(
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "urn:schemas-upnp-org:service:AVTransport:1"
        )
    }
}
