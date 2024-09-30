package com.futo.platformplayer.sync.models

import com.futo.platformplayer.models.Subscription
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.util.Dictionary

@Serializable
class SyncSubscriptionsPackage(
    var subscriptions: List<Subscription>,
    var subscriptionRemovals: Map<String, Long>
)