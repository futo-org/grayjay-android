package com.futo.platformplayer.sync

import com.futo.platformplayer.serializers.OffsetDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
class SyncSessionData(var publicKey: String) {
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastHistory: OffsetDateTime = OffsetDateTime.MIN;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeSerializer::class)
    var lastSubscription: OffsetDateTime = OffsetDateTime.MIN;
}