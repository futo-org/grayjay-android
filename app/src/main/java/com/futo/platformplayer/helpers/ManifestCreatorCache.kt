package com.futo.platformplayer.helpers

import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap

class ManifestCreatorCache<K : Serializable, V : Serializable?> : Serializable {
    private val concurrentHashMap: ConcurrentHashMap<K, Pair<Int, V>>
    private var maximumSize: Int = DEFAULT_MAXIMUM_SIZE
    private var clearFactor: Double = DEFAULT_CLEAR_FACTOR

    init {
        concurrentHashMap = ConcurrentHashMap<K, Pair<Int, V>>()
    }

    fun containsKey(key: K): Boolean {
        return concurrentHashMap.containsKey(key)
    }

    operator fun get(key: K): Pair<Int, V>? {
        return concurrentHashMap[key]
    }

    fun put(key: K, value: V): V? {
        if (!concurrentHashMap.containsKey(key) && concurrentHashMap.size == maximumSize) {
            val newCacheSize = Math.round(maximumSize * clearFactor).toInt()
            keepNewestEntries(if (newCacheSize != 0) newCacheSize else 1)
        }
        val returnValue: Pair<Int, V>? = concurrentHashMap.put(key, Pair<Int, V>(concurrentHashMap.size, value))
        return if (returnValue == null) null else returnValue.second
    }

    fun clear() {
        concurrentHashMap.clear()
    }

    fun size(): Int {
        return concurrentHashMap.size
    }

    override fun toString(): String {
        return "ManifestCreatorCache[clearFactor=$clearFactor, maximumSize=$maximumSize, concurrentHashMap=$concurrentHashMap]"
    }

    private fun keepNewestEntries(newLimit: Int) {
        val difference = concurrentHashMap.size - newLimit
        val entriesToRemove: ArrayList<Map.Entry<K, Pair<Int, V>>> = ArrayList()
        concurrentHashMap.entries.forEach { entry: MutableMap.MutableEntry<K, Pair<Int, V>> ->
            val value: Pair<Int, V> = entry.value
            if (value.first < difference) {
                entriesToRemove.add(entry)
            } else {
                entry.setValue(value.copy(value.first - difference, value.second))
            }
        }
        entriesToRemove.forEach { (key, value): Map.Entry<K, Pair<Int, V>> ->
            concurrentHashMap.remove(key, value)
        }
    }

    companion object {
        const val DEFAULT_MAXIMUM_SIZE = Int.MAX_VALUE
        const val DEFAULT_CLEAR_FACTOR = 0.75
    }
}