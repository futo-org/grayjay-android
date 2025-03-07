package com.futo.platformplayer.stores

import kotlinx.serialization.*
import kotlinx.serialization.json.Json

@Serializable()
class DownloadsOrderingStorage : FragmentedStorageFileJson() {
    var ordering = "downloadDateDesc";

    fun update(ord: String) {
        ordering = ord
        save();
    }
    override fun encode(): String {
        return Json.encodeToString(this);
    }
}