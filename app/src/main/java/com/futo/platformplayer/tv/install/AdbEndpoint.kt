package com.futo.platformplayer.tv.install

import java.net.Inet6Address
import java.net.InetAddress

data class AdbEndpoint(val host: String, val port: Int) {
    val serial: String
        get() = if (host.contains(':')) "[$host]:$port" else "$host:$port"

    init {
        require(port in 1..65535) { "Invalid port" }
        require(isValidHost(host)) { "Invalid address" }
    }

    companion object {
        fun parse(address: String, defaultPort: Int? = null): AdbEndpoint {
            val match = Regex("(\\[[0-9a-fA-F:.]+\\]|[^:\\s]+)(?::([0-9]+))?").matchEntire(address.trim())
                ?: throw IllegalArgumentException("Invalid address")
            val port = match.groups[2]?.value?.toInt() ?: defaultPort
                ?: throw IllegalArgumentException("Missing port")
            return parse(match.groupValues[1], port.toString())
        }

        fun parse(host: String, port: String): AdbEndpoint {
            return AdbEndpoint(host.trim().removeSurrounding("[", "]"), port.trim().toInt())
        }

        private fun isValidHost(host: String): Boolean {
            if (host.isEmpty() || host.length > 253 || host.any { it.isWhitespace() })
                return false
            if (host.contains(':')) {
                if (!host.matches(Regex("[0-9a-fA-F:.]+")))
                    return false
                return runCatching { InetAddress.getByName(host) is Inet6Address }.getOrDefault(false)
            }
            if (host.matches(Regex("[0-9.]+"))) {
                val parts = host.split('.')
                return parts.size == 4 && parts.all { part ->
                    part.isNotEmpty() && part.length <= 3 && part.toIntOrNull() in 0..255
                }
            }
            return host.split('.').all {
                it.length in 1..63 && it.matches(Regex("[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?"))
            }
        }
    }
}
