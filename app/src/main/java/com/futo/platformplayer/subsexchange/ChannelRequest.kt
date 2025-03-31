package com.futo.platformplayer.subsexchange

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChannelRequest(
    @SerialName("ChannelUrl")
    var channelUrl: String
);