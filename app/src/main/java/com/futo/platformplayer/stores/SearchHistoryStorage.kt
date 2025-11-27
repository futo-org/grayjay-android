package com.futo.platformplayer.stores

import kotlinx.serialization.*
import kotlinx.serialization.json.Json

@Serializable()
class SearchHistoryStorage : FragmentedStorageFileJson() {
    var lastQueries = arrayListOf<String>();

    fun add(text: String) {
        if (!lastQueries.contains(text)) {
            lastQueries.add(0, text);
            if (lastQueries.size > 10)
                lastQueries.removeAt(lastQueries.size - 1);
        }
        else {
            lastQueries.remove(text);
            lastQueries.add(0, text);
        }
        save();
    }

    override fun encode(): String {
        return Json.encodeToString(this);
    }
}