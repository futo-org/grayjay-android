package com.futo.platformplayer.sync.models

import com.futo.platformplayer.models.Playlist
import com.futo.platformplayer.models.Subscription
import com.futo.platformplayer.models.SubscriptionGroup
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.util.Dictionary

@Serializable
class SyncPlaylistsPackage(
    var playlists: List<Playlist>,
    var playlistRemovals: Map<String, Long>
)