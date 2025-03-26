package com.futo.platformplayer.subsexchange

import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.serializers.OffsetDateTimeNullableSerializer
import com.futo.platformplayer.serializers.OffsetDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
class ChannelResult(
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var dateTime: OffsetDateTime,
    var channelUrl: String,
    var content: List<IPlatformContent>,
    var channel: IPlatformChannel? = null
)