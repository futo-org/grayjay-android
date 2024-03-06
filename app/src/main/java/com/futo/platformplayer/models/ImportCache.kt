package com.futo.platformplayer.models

import com.futo.platformplayer.api.media.models.channels.SerializedChannel
import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import kotlinx.serialization.Serializable

@Serializable
class ImportCache(
    var videos: List<SerializedPlatformVideo>? = null,
    var channels: List<SerializedChannel>? = null
);