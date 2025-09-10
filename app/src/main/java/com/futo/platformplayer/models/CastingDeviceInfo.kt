package com.futo.platformplayer.models

import com.futo.platformplayer.casting.CastProtocolType

@kotlinx.serialization.Serializable
class CastingDeviceInfo(
    var name: String,
    var type: CastProtocolType,
    var addresses: Array<String>,
    var port: Int
)