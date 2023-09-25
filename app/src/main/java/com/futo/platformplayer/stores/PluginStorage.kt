package com.futo.platformplayer.stores

import com.futo.platformplayer.api.media.platforms.js.SourcePluginDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@kotlinx.serialization.Serializable
class PluginStorage : FragmentedStorageFileJson() {

    var sourcePlugins = mutableListOf<SourcePluginDescriptor>();

    override fun encode(): String {
        return Json.encodeToString(this);
    }
}