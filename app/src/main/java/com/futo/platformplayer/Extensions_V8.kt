package com.futo.platformplayer

import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.primitive.*
import com.caoccao.javet.values.reference.V8ValueArray
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.exceptions.ScriptImplementationException


//V8
fun <R> V8Value?.orNull(handler: (V8Value)->R) : R? {
    if(this == null)
        return null;
    if(this is V8ValueNull || this is V8ValueUndefined || (this is V8ValueDouble && this.isNaN))
        return null;
    else
        return handler(this);
}
fun <R> V8Value?.orDefault(default: R, handler: (V8Value)->R): R {
    if(this == null || this is V8ValueNull || this is V8ValueUndefined)
        return default;
    else
        return handler(this);
}

inline fun <reified T> V8Value.expectOrThrow(config: IV8PluginConfig, contextName: String): T  {
    if(this !is T)
        throw ScriptImplementationException(config, "Expected ${contextName} to be of type ${T::class.simpleName}, but found ${this::class.simpleName}");
    return this as T;
}

//Singles
inline fun <reified T> V8ValueObject.getOrThrowNullable(config: IV8PluginConfig, key: String, contextName: String): T? = getOrThrow(config, key, contextName, true);
inline fun <reified T> V8ValueObject.getOrThrow(config: IV8PluginConfig, key: String, contextName: String, nullable: Boolean = false): T {
    val value = this.get<V8Value>(key);
    if(nullable)
        return value.orNull { value.expectV8Variant<T>(config, "${contextName}.${key}") } as T
    else
        return value.expectV8Variant(config, "${contextName}.${key}");
}
inline fun <reified T> V8ValueObject.getOrNull(config: IV8PluginConfig, key: String, contextName: String): T? {
    val value = this.get<V8Value>(key);
    return value.orNull { value.expectV8Variant<T>(config, "${contextName}.${key}") };
}
inline fun <reified T> V8ValueObject.getOrDefault(config: IV8PluginConfig, key: String, contextName: String, default: T?): T? {
    val value = this.get<V8Value>(key);
    return value.orNull { value.expectV8Variant<T>(config, "${contextName}.${key}") } ?: default;
}

//Lists

inline fun <reified T> V8ValueObject.getOrThrowNullableList(config: IV8PluginConfig, key: String, contextName: String): List<T>? = getOrThrowList(config, key, contextName, true);
inline fun <reified T> V8ValueObject.getOrThrowList(config: IV8PluginConfig, key: String, contextName: String, nullable: Boolean = false): List<T> {
    val value = this.get<V8Value>(key);
    val array = if(nullable)
        value.orNull { value.expectV8Variant<V8ValueArray>(config, "${contextName}.${key}") }
    else
        value.expectV8Variant<V8ValueArray>(config, "${contextName}.${key}");
    if(array == null)
        return listOf();

    return array.expectV8Variants(config, contextName, false);
}
inline fun <reified T> V8ValueObject.getOrNullList(config: IV8PluginConfig, key: String, contextName: String): List<T>? {
    val value = this.get<V8Value>(key);
    val array = value.orNull { value.expectV8Variant<V8ValueArray>(config, "${contextName}.${key}") }
        ?: return null;

    return array.expectV8Variants(config, contextName, true);
}
inline fun <reified T> V8ValueObject.getOrDefaultList(config: IV8PluginConfig, key: String, contextName: String, default: List<T>?): List<T>? {
    val value = this.get<V8Value>(key);
    val array = value.orNull { value.expectV8Variant<V8ValueArray>(config, "${contextName}.${key}") }
        ?: return default;

    return array.expectV8Variants<T>(config, contextName, true);
}

inline fun <reified T> V8ValueArray.expectV8Variants(config: IV8PluginConfig, contextName: String, nullable: Boolean): List<T> {
    val array = this;
    if(nullable)
        return array.keys
            .map { Pair(it, array.get<V8Value>(it)) }
            .map { kv-> kv.second.orNull { it.expectV8Variant<T>(config, contextName + "[${kv.first}]", ) } as T };
    else
        return array.keys
            .map { Pair(it, array.get<V8Value>(it)) }
            .map { kv-> kv.second.orNull { it.expectV8Variant<T>(config, contextName + "[${kv.first}]", ) } as T };
}

inline fun <reified T> V8Value.expectV8Variant(config: IV8PluginConfig, contextName: String): T {
    return when(T::class) {
        String::class -> this.expectOrThrow<V8ValueString>(config, contextName).value as T;
        Int::class -> {
            if(this is V8ValueDouble)
                return this.value.toInt() as T;
            else if(this is V8ValueInteger)
                return this.value.toInt() as T;
            else if(this is V8ValueLong)
                return this.value.toInt() as T;
            else this.expectOrThrow<V8ValueInteger>(config, contextName).value as T
        };
        Long::class -> {
            if(this is V8ValueDouble)
                return this.value.toLong() as T;
            else if(this is V8ValueInteger)
                return this.value.toLong() as T;
            else
                return this.expectOrThrow<V8ValueLong>(config, contextName).value.toLong() as T
        };
        Float::class -> {
            if(this is V8ValueDouble)
                return this.value.toFloat() as T;
            else if(this is V8ValueInteger)
                return this.value.toFloat() as T;
            else if(this is V8ValueLong)
                return this.value.toFloat() as T;
            else
                return this.expectOrThrow<V8ValueDouble>(config, contextName).value.toDouble() as T
        };
        Double::class -> {
            if(this is V8ValueDouble)
                return this.value.toDouble() as T;
            else if(this is V8ValueInteger)
                return this.value.toDouble() as T;
            else if(this is V8ValueLong)
                return this.value.toDouble() as T;
            else
                return this.expectOrThrow<V8ValueDouble>(config, contextName).value.toDouble() as T
        };
        V8ValueObject::class -> this.expectOrThrow<V8ValueObject>(config, contextName) as T
        V8ValueArray::class -> this.expectOrThrow<V8ValueArray>(config, contextName) as T;
        Boolean::class -> this.expectOrThrow<V8ValueBoolean>(config, contextName).value as T;
        HashMap::class -> this.expectOrThrow<V8ValueObject>(config, contextName).let { V8ObjectToHashMap(it) } as T;
        Map::class -> this.expectOrThrow<V8ValueObject>(config, contextName).let { V8ObjectToHashMap(it) } as T;
        List::class -> this.expectOrThrow<V8ValueArray>(config, contextName).let { V8ArrayToStringList(it) } as T;
        else -> throw NotImplementedError("Type ${T::class.simpleName} not implemented conversion");
    }
}
fun V8ArrayToStringList(obj: V8ValueArray): List<String> = obj.keys.map { obj.getString(it) };
fun V8ObjectToHashMap(obj: V8ValueObject?): HashMap<String, String> {
    if(obj == null)
        return hashMapOf();
    val map = hashMapOf<String, String>();
    for(prop in obj.ownPropertyNames.keys.map { obj.ownPropertyNames.get<V8Value>(it).toString() })
        map.put(prop, obj.getString(prop));
    return map;
}