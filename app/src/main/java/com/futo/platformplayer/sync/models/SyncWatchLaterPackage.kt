package com.futo.platformplayer.sync.models

import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.models.SubscriptionGroup
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.util.Dictionary

@Serializable
class SyncWatchLaterPackage(
    var videos: List<SerializedPlatformVideo>,
    var videoAdds: Map<String, Long>,
    var videoRemovals: Map<String, Long>,
    var reorderTime: Long = 0,
    var ordering: List<String>? = null
)