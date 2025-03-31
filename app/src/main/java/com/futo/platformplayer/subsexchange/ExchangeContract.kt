package com.futo.platformplayer.subsexchange

import com.futo.platformplayer.serializers.OffsetDateTimeNullableSerializer
import com.futo.platformplayer.serializers.OffsetDateTimeSerializer
import com.futo.platformplayer.serializers.OffsetDateTimeStringSerializer
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import java.time.OffsetDateTime

@Serializable
class ExchangeContract(
    @SerialName("ID")
    var id: String,
    @SerialName("Requests")
    var requests: List<ChannelRequest>,
    @SerialName("Provided")
    var provided: List<String> = listOf(),
    @SerialName("Required")
    var required: List<String> = listOf(),
    @SerialName("Expire")
    @kotlinx.serialization.Serializable(with = OffsetDateTimeStringSerializer::class)
    var expired: OffsetDateTime = OffsetDateTime.MIN,
    @SerialName("ContractVersion")
    var contractVersion: Int = 1
)