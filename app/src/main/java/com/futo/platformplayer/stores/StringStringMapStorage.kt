package com.futo.platformplayer.stores

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@kotlinx.serialization.Serializable
class StringStringMapStorage : FragmentedStorageFileJson() {
    var map: HashMap<String, String> = hashMapOf()

    override fun encode(): String {
        return Json.encodeToString(this)
    }

    fun get(key: String): String? {
        return map[key]
    }

    fun setAndSave(key: String, value: String): String {
        map[key] = value
        save()
        return value
    }

    fun setAndSaveBlocking(key: String, value: String): String {
        map[key] = value
        saveBlocking()
        return value
    }
}
