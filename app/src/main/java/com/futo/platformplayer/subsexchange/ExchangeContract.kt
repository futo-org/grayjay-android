package com.futo.platformplayer.subsexchange

import com.futo.platformplayer.serializers.OffsetDateTimeNullableSerializer
import com.futo.platformplayer.serializers.OffsetDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
class ExchangeContract(
    var id: String,
    var requests: List<ChannelRequest>,
    var provided: List<String> = listOf(),
    var required: List<String> = listOf(),
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var expired: OffsetDateTime = OffsetDateTime.MIN,
    var contractVersion: Int = 1
)