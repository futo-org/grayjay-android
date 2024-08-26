package com.futo.platformplayer.mdns

data class BroadcastService(
    val deviceName: String,
    val serviceName: String,
    val port: UShort,
    val ttl: UInt,
    val weight: UShort,
    val priority: UShort,
    val texts: List<String>? = null
)