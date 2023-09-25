package com.futo.platformplayer.stores

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@kotlinx.serialization.Serializable
class StringStorage : FragmentedStorageFileJson() {

    var value : String = "";

    override fun encode(): String {
        return Json.encodeToString(this);
    }

    fun setAndSave(str: String) : String {
        value = str;
        save();
        return value;
    }

    fun setAndSaveBlocking(str: String) : String {
        value = str;
        saveBlocking();
        return value;
    }
}