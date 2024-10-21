package com.futo.platformplayer.stores

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Dictionary
import java.util.concurrent.ConcurrentHashMap

@kotlinx.serialization.Serializable
class StringDateMapStorage : FragmentedStorageFileJson() {
    var map: HashMap<String, Long> = hashMapOf()

    override fun encode(): String {
        synchronized(map) {
            return Json.encodeToString(this);
        }
    }

    fun get(key: String): OffsetDateTime? {
        synchronized(map) {
            val v = map[key];
            if (v == null)
                return null;
            return OffsetDateTime.of(
                LocalDateTime.ofEpochSecond(v, 0, ZoneOffset.UTC),
                ZoneOffset.UTC
            );
        }
    }
    fun has(key: String): Boolean {
        return map.contains(key);
    }

    fun all(): Map<String, Long>{
        synchronized(map) {
            return map.toMap();
        }
    }

    fun setAllAndSave(newValues: Map<String, Long>, condition: ((String, Long, Long?) -> Boolean)? = null) {
        synchronized(map){
            for(kv in newValues){
                if(condition == null || condition(kv.key, kv.value, map.get(kv.key)))
                    map.set(kv.key, kv.value);
            }
        }
    }
    fun setAndSave(key: String, value: OffsetDateTime): OffsetDateTime {
        synchronized(map) {
            map[key] = value.toEpochSecond();
            save()
            return value
        }
    }

    fun setAndSaveBlocking(key: String, value: OffsetDateTime): OffsetDateTime {
        synchronized(map) {
            map[key] = value.toEpochSecond();
            saveBlocking()
            return value
        }
    }
}
