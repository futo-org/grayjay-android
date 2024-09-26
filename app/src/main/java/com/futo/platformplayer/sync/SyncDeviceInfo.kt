package com.futo.platformplayer.sync

@kotlinx.serialization.Serializable
class SyncDeviceInfo {
    var publicKey: String
    var addresses: Array<String>
    var port: Int

    constructor(publicKey: String, addresses: Array<String>, port: Int) {
        this.publicKey = publicKey
        this.addresses = addresses
        this.port = port
    }
}