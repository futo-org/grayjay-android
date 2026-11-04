package com.futo.platformplayer.tv.install

import kotlinx.serialization.Serializable

@Serializable
data class TvInstallDevice(
    val name: String,
    val host: String,
    val port: Int,
    val usesPairing: Boolean = true,
    val installedVersion: String? = null,
    val syncPublicKey: String? = null
) {
    val endpoint: AdbEndpoint
        get() = AdbEndpoint(host, port)
}
