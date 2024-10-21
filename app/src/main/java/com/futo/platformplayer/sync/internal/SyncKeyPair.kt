package com.futo.platformplayer.sync.internal

@kotlinx.serialization.Serializable
class SyncKeyPair {
    var publicKey: String
    var privateKey: String
    var version: Int

    constructor(version: Int, publicKey: String, privateKey: String) {
        this.publicKey = publicKey
        this.privateKey = privateKey
        this.version = version
    }
}