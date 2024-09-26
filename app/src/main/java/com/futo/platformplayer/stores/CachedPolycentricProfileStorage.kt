package com.futo.platformplayer.stores

import com.futo.platformplayer.polycentric.PolycentricCache
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@kotlinx.serialization.Serializable
class CachedPolycentricProfileStorage : FragmentedStorageFileJson() {
    var map: HashMap<String, PolycentricCache.CachedPolycentricProfile> = hashMapOf();

    override fun encode(): String {
        val encoded = Json.encodeToString(this);
        return encoded;
    }

    fun get(key: String) : PolycentricCache.CachedPolycentricProfile? {
        return map[key];
    }

    fun setAndSave(key: String, value: PolycentricCache.CachedPolycentricProfile) : PolycentricCache.CachedPolycentricProfile {
        map[key] = value;
        save();
        return value;
    }

    fun setAndSaveBlocking(key: String, value: PolycentricCache.CachedPolycentricProfile) : PolycentricCache.CachedPolycentricProfile {
        map[key] = value;
        saveBlocking();
        return value;
    }
}