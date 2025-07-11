package com.futo.platformplayer

import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.primitive.*
import com.caoccao.javet.values.reference.IV8ValuePromise
import com.caoccao.javet.values.reference.V8ValueArray
import com.caoccao.javet.values.reference.V8ValueError
import com.caoccao.javet.values.reference.V8ValueObject
import com.caoccao.javet.values.reference.V8ValuePromise
import com.futo.platformplayer.engine.IV8PluginConfig
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.engine.exceptions.ScriptExecutionException
import com.futo.platformplayer.engine.exceptions.ScriptImplementationException
import com.futo.platformplayer.logging.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.selects.SelectClause0
import kotlinx.coroutines.selects.SelectClause1
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.jvm.internal.impl.load.kotlin.JvmType


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

inline fun V8Value.getSourcePlugin(): V8Plugin? {
    return V8Plugin.getPluginFromRuntime(this.v8Runtime);
}

inline fun <reified T> V8Value.expectOrThrow(config: IV8PluginConfig, contextName: String): T  {
    if(this !is T)
        throw ScriptImplementationException(config, "Expected ${contextName} to be of type ${T::class.simpleName}, but found ${this::class.simpleName}");
    return this;
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

inline fun V8Plugin.ensureIsBusy() {
    this.let {
        if (!it.isThreadAlreadyBusy()) {
            //throw IllegalStateException("Tried to access V8Plugin without busy");
            val stacktrace = Thread.currentThread().stackTrace;
            Logger.w("Extensions_V8",
                "V8 USE OUTSIDE BUSY: " + stacktrace.drop(3)?.firstOrNull().toString() +
                        ", " + stacktrace.drop(4)?.firstOrNull().toString() +
                        ", " + stacktrace.drop(5)?.firstOrNull()?.toString() +
                        ", " + stacktrace.drop(6)?.firstOrNull()?.toString()
            );
        }
    }
}
inline fun V8Value.ensureIsBusy() {
    this?.getSourcePlugin()?.let {
        it.ensureIsBusy();
    }
}

inline fun <reified T> V8Value.expectV8Variant(config: IV8PluginConfig, contextName: String): T {
    if(false)
        ensureIsBusy();
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


fun <T: V8Value> V8ValuePromise.toV8ValueBlocking(plugin: V8Plugin): T {
    val latch = CountDownLatch(1);
    var promiseResult: T? = null;
    var promiseException: Throwable? = null;
    plugin.busy {
        this.register(object: IV8ValuePromise.IListener {
            override fun onFulfilled(p0: V8Value?) {
                if(p0 is V8ValueError)
                    promiseException = ScriptExecutionException(plugin.config, p0.message);
                else
                    promiseResult = p0 as T;
                latch.countDown();
            }
            override fun onRejected(p0: V8Value?) {
                promiseException = (NotImplementedError("onRejected promise not implemented.."));
                latch.countDown();
            }
            override fun onCatch(p0: V8Value?) {
                promiseException = (NotImplementedError("onCatch promise not implemented.."));
                latch.countDown();
            }
        });
    }

    plugin.registerPromise(this) {
        promiseException =  CancellationException("Cancelled by system");
        latch.countDown();
    }
    plugin.unbusy {
        latch.await();
    }
    if(promiseException != null)
        throw promiseException!!;
    return promiseResult!!;
}
fun <T: V8Value> V8ValuePromise.toV8ValueAsync(plugin: V8Plugin): V8Deferred<T> {
    val underlyingDef = CompletableDeferred<T>();
    val def = if(this.has("estDuration"))
        V8Deferred(underlyingDef,
            this.getOrDefault(plugin.config, "estDuration", "toV8ValueAsync", -1) ?: -1);
    else
        V8Deferred<T>(underlyingDef);

    if(def.estDuration > 0)
        Logger.i("V8", "Promise with duration: [${def.estDuration}]");

    val promise = this;
    plugin.busy {
        this.register(object: IV8ValuePromise.IListener {
            override fun onFulfilled(p0: V8Value?) {
                plugin.resolvePromise(promise);
                underlyingDef.complete(p0 as T);
            }
            override fun onRejected(p0: V8Value?) {
                plugin.resolvePromise(promise);
                underlyingDef.completeExceptionally(NotImplementedError("onRejected promise not implemented.."));
            }
            override fun onCatch(p0: V8Value?) {
                plugin.resolvePromise(promise);
                underlyingDef.completeExceptionally(NotImplementedError("onCatch promise not implemented.."));
            }
        });
    }
    plugin.registerPromise(promise) {
        if(def.isActive)
            def.cancel("Cancelled by system");
    }
    return def;
}

class V8Deferred<T>(val deferred: Deferred<T>, val estDuration: Int = -1): Deferred<T> by deferred {

    fun <R> convert(conversion: (result: T)->R): V8Deferred<R>{
        val newDef = CompletableDeferred<R>()
        this.invokeOnCompletion { 
            if(it != null)
                newDef.completeExceptionally(it);
            else
                newDef.complete(conversion(this@V8Deferred.getCompleted()));
        }
        
        return V8Deferred<R>(newDef, estDuration);
    }


    companion object {
        fun <T, R> merge(scope: CoroutineScope, defs: List<V8Deferred<T>>, conversion: (result: List<T>)->R): V8Deferred<R> {

            var amount = -1;
            for(def in defs)
                amount = Math.max(amount, def.estDuration);

            val def = scope.async {
                val results = defs.map { it.await() };
                return@async conversion(results);
            }
            return V8Deferred(def, amount);
        }
    }
}


fun <T: V8Value> V8ValueObject.invokeV8(method: String, vararg obj: Any?): T {
    var result = this.invoke<V8Value>(method, *obj);
    if(result is V8ValuePromise) {
        return result.toV8ValueBlocking(this.getSourcePlugin()!!);
    }
    return result as T;
}
fun <T: V8Value> V8ValueObject.invokeV8Async(method: String, vararg obj: Any?): V8Deferred<T> {
    var result = this.invoke<V8Value>(method, *obj);
    if(result is V8ValuePromise) {
        return result.toV8ValueAsync(this.getSourcePlugin()!!);
    }
    return V8Deferred(CompletableDeferred(result as T));
}
fun V8ValueObject.invokeV8Void(method: String, vararg obj: Any?): V8Value {
    var result = this.invoke<V8Value>(method, *obj);
    if(result is V8ValuePromise) {
        return result.toV8ValueBlocking(this.getSourcePlugin()!!);
    }
    return result;
}
fun V8ValueObject.invokeV8VoidAsync(method: String, vararg obj: Any?): V8Deferred<V8Value> {
    var result = this.invoke<V8Value>(method, *obj);
    if(result is V8ValuePromise) {
        val result = result.toV8ValueAsync<V8Value>(this.getSourcePlugin()!!);
        return result;
    }
    return V8Deferred(CompletableDeferred(result));
}