package com.futo.platformplayer.sync.internal

@kotlinx.serialization.Serializable
class SyncDeviceInfo {
    var publicKey: String
    var addresses: Array<String>
    var port: Int
    var pairingCode: String?

    constructor(publicKey: String, addresses: Array<String>, port: Int, pairingCode: String?) {
        this.publicKey = publicKey
        this.addresses = addresses
        this.port = port
        this.pairingCode = pairingCode
    }
}