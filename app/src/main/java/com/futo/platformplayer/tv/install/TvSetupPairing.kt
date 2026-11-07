package com.futo.platformplayer.tv.install

import com.futo.platformplayer.sync.internal.SyncDeviceInfo
import kotlinx.serialization.json.Json
import java.util.Base64

object TvSetupPairing {
    fun fromBroadcast(output: String): SyncDeviceInfo? {
        val url = Regex("data=\"(grayjay://sync/[A-Za-z0-9_-]+)\"").find(output)?.groupValues?.get(1) ?: return null
        return decode(url)
    }

    fun decode(url: String): SyncDeviceInfo {
        require(url.startsWith("grayjay://sync/") && url.length <= 8192)
        val info = Json.decodeFromString<SyncDeviceInfo>(
            Base64.getUrlDecoder().decode(url.removePrefix("grayjay://sync/")).decodeToString())
        require(Base64.getDecoder().decode(info.publicKey).size == 32)
        require(info.port in 1..65535 && !info.pairingCode.isNullOrBlank())
        require(info.addresses.size <= 32)
        info.addresses.forEach { AdbEndpoint(it, info.port) }
        return info
    }
}
