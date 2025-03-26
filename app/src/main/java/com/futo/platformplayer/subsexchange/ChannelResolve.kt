package com.futo.platformplayer.subsexchange

import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
class ChannelResolve(
    var channelUrl: String,
    var content: List<IPlatformContent>,
    var channel: IPlatformChannel? = null
)