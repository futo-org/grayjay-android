package com.futo.platformplayer.api.media.models

import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.primitive.V8ValueInteger
import com.caoccao.javet.values.reference.V8ValueArray
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.expectV8Variant
import com.futo.platformplayer.getOrDefault
import com.futo.platformplayer.getOrThrow


class ResultCapabilities(
    val types: List<String> = listOf(),
    val sorts: List<String> = listOf(),
    val filters: List<FilterGroup> = listOf()
) {

    fun hasType(type: String): Boolean {
        return types.contains(type);
    }
    fun hasSort(sort: String): Boolean {
        return sorts.contains(sort);
    }

    companion object {
        const val TYPE_VIDEOS = "VIDEOS";
        const val TYPE_STREAMS = "STREAMS";
        const val TYPE_LIVE = "LIVE";
        const val TYPE_POSTS = "POSTS";
        const val TYPE_MIXED = "MIXED";
        const val TYPE_SUBSCRIPTIONS = "SUBSCRIPTIONS";
        const val TYPE_SHORTS = "SHORTS";

        const val ORDER_CHONOLOGICAL = "CHRONOLOGICAL";

        const val DATE_LAST_HOUR = "LAST_HOUR";
        const val DATE_TODAY = "TODAY";
        const val DATE_LAST_WEEK = "LAST_WEEK";
        const val DATE_LAST_MONTH = "LAST_MONTH";
        const val DATE_LAST_YEAR = "LAST_YEAR";

        const val DURATION_SHORT = "SHORT";
        const val DURATION_MEDIUM = "MEDIUM";
        const val DURATION_LONG = "LONG";

        fun fromV8(config: IV8PluginConfig, value: V8ValueObject): ResultCapabilities {
            val contextName = "ResultCapabilities";
            return ResultCapabilities(
                value.getOrThrow<V8ValueArray>(config, "types", contextName).toArray().map { it.expectV8Variant(config, "Capabilities.types") },
                value.getOrThrow<V8ValueArray>(config, "sorts", contextName).toArray().map { it.expectV8Variant(config, "Capabilities.sorts"); },
                value.getOrDefault<V8ValueArray>(config, "filters", contextName, null)
                    ?.toArray()
                    ?.map { FilterGroup.fromV8(config, it as V8ValueObject) }
                    ?.toList() ?: listOf());
        }
    }
}


@kotlinx.serialization.Serializable
class FilterGroup(
    val name: String,
    val filters: List<FilterCapability> = listOf(),
    val isMultiSelect: Boolean,
    val id: String? = null
) {
    val idOrName: String get() = id ?: name;

    companion object {
        fun fromV8(config: IV8PluginConfig, value: V8ValueObject): FilterGroup {
            return FilterGroup(
                value.getString("name"),
                value.getOrDefault<V8ValueArray>(config, "filters", "FilterGroup", null)
                    ?.toArray()
                    ?.map { FilterCapability.fromV8(it as V8ValueObject) }
                    ?.toList() ?: listOf(),
                value.getBoolean("isMultiSelect"),
                value.getString("id"));
        }
    }
}

@kotlinx.serialization.Serializable
class FilterCapability(
    val name: String,
    val value: String,
    val id: String? = null) {
    val idOrName: String get() = id ?: name;

    companion object {
        fun fromV8(obj: V8ValueObject): FilterCapability {
            val value = obj.get("value") as V8Value;
            return FilterCapability(
                obj.getString("name"),
                if(value is V8ValueInteger)
                    value.value.toString()
                else
                    value.toString(),
                obj.getString("id")
            );
        }
    }
}