package com.futo.platformplayer.stores

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

abstract class FragmentedStorageFileJson : FragmentedStorageFile() {

    override fun encode(): String {
        return Json.encodeToString(this);
    }
}