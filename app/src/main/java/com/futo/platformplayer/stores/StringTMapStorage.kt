package com.futo.platformplayer.stores

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@kotlinx.serialization.Serializable
class StringTMapStorage<T> : FragmentedStorageFileJson() {
    var map: HashMap<String, T> = hashMapOf()

    override fun encode(): String {
        return Json.encodeToString(this)
    }

    fun get(key: String): T? {
        return map[key]
    }

    fun setAndSave(key: String, value: T): T {
        map[key] = value
        save()
        return value
    }

    fun setAndSaveBlocking(key: String, value: T): T {
        map[key] = value
        saveBlocking()
        return value
    }
}
