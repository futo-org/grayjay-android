package com.futo.platformplayer.stores

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@kotlinx.serialization.Serializable
class StringHashSetStorage : FragmentedStorageFileJson() {

    var values = HashSet<String>();

    override fun encode(): String {
        return Json.encodeToString(this);
    }

    fun contains(obj: String): Boolean {
        synchronized(values) {
            return values.contains(obj);
        }
    }

    fun add(obj: String) {
        synchronized(values) {
            values.add(obj)
        }
    }
    fun addDistinct(obj: String) {
        synchronized(values) {
            if(!values.contains(obj))
                values.add(obj);
        }
    }

    fun remove(obj: String) {
        synchronized(values) {
            values.remove(obj);
        }
    }
    fun set(vararg objs: String) {
        synchronized(values) {
            values.clear();
            values.addAll(objs);
        }
    }

    fun getAllValues(): List<String> {
        synchronized(values){
            return values.toList();
        }
    }
}