package com.futo.platformplayer.sync.models

import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.models.SubscriptionGroup
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.util.Dictionary

@Serializable
class SyncSubscriptionGroupsPackage(
    var groups: List<SubscriptionGroup>,
    var groupRemovals: Map<String, Long>
)