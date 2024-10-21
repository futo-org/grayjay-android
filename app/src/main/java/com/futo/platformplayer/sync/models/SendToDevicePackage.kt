package com.futo.platformplayer.sync.models

import kotlinx.serialization.Serializable

@Serializable
class SendToDevicePackage(
    var url: String,
    var position: Int = 0
)