package com.futo.platformplayer.subsexchange

import com.futo.platformplayer.api.media.models.channels.IPlatformChannel
import com.futo.platformplayer.api.media.models.contents.IPlatformContent
import com.futo.platformplayer.api.media.models.video.SerializedPlatformContent
import com.futo.platformplayer.serializers.OffsetDateTimeNullableSerializer
import com.futo.platformplayer.serializers.OffsetDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
class ChannelResult(
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    @SerialName("DateTime")
    var dateTime: OffsetDateTime,
    @SerialName("ChannelUrl")
    var channelUrl: String,
    @SerialName("Content")
    var content: List<SerializedPlatformContent>,
    @SerialName("Channel")
    var channel: IPlatformChannel? = null
)