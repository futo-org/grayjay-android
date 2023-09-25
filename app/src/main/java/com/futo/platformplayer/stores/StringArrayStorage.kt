package com.futo.platformplayer.stores

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@kotlinx.serialization.Serializable
class StringArrayStorage : FragmentedStorageFileJson() {

    var values = mutableListOf<String>();

    override fun encode(): String {
        return Json.encodeToString(this);
    }

    fun add(obj: String) {
        synchronized(values) {
            values.add(obj)
        }
    }
    fun addDistinct(obj: String) {
        synchronized(values) {
            if(!values.any { it == obj })
                values.add(obj);
        }
    }

    fun remove(obj: String) {
        synchronized(values) {
            values.removeIf { it == obj };
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