package com.futo.platformplayer.models

import com.futo.platformplayer.casting.CastProtocolType

@kotlinx.serialization.Serializable
class CastingDeviceInfo {
    var name: String;
    var type: CastProtocolType;
    var addresses: Array<String>;
    var port: Int;

    constructor(name: String, type: CastProtocolType, addresses: Array<String>, port: Int) {
        this.name = name;
        this.type = type;
        this.addresses = addresses;
        this.port = port;
    }
}