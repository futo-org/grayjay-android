package com.futo.platformplayer.stores

import com.futo.platformplayer.api.media.models.video.SerializedPlatformVideo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@kotlinx.serialization.Serializable
class WatchLaterStorage : FragmentedStorageFileJson() {

    var playlist = listOf<SerializedPlatformVideo>();

    override fun encode(): String {
        return Json.encodeToString(this);
    }
}