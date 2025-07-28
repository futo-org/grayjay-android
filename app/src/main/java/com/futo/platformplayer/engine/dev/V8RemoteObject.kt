package com.futo.platformplayer.engine.dev

import com.caoccao.javet.annotations.V8Function
import com.caoccao.javet.annotations.V8Property
import com.futo.platformplayer.logging.Logger
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.util.stream.IntStream.range
import kotlin.reflect.*
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.jvm.javaMethod


/**
 * Serializable object wrapper that communicates complex V8 objects to a format understood by the dev portal
 * It allows plugins in development to communicate with package logic on the phone
 * It does this by embedding object ids and function names into the object, which dev portal can then intercept and call via special endpoints
 */
class V8RemoteObject {
    private val _id: String;
    private val _class: KClass<*>;
    val obj: Any;

    val requiresRegistration: Boolean;

    constructor(id: String, obj: Any) {
        this._id = id;
        this._class = obj::class;
        this.obj = obj;
        this.requiresRegistration = getV8Functions(_class).isNotEmpty() || getV8Properties(_class).isNotEmpty();
    }

    fun prop(propName: String): Any? {
        val propMethod = getV8Property(_class, propName);
        return propMethod.call(obj);
    }
    fun call(methodName: String, array: JsonArray): Any? {
        val propMethod = getV8Function(_class, methodName);

        val map = mutableMapOf<KParameter, Any?>();
        var instanceParaCount = 0;
        for(i in range(0, propMethod.parameters.size)) {
            val para = propMethod.parameters[i];
            if(para == propMethod.instanceParameter) {
                map.put(para, obj);
                instanceParaCount++;
            }
            else if(i - instanceParaCount < array.size())
                map.put(para, gsonStandard.fromJson(array.get(i - instanceParaCount), propMethod.javaMethod!!.parameterTypes[i - instanceParaCount]));
        }

        return propMethod.callBy(map)
    }


    fun serialize(): String {
        return _gson.toJson(this);
    }

    class Serializer : JsonSerializer<V8RemoteObject> {
        override fun serialize(src: V8RemoteObject?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            try {
                if (src == null)
                    return JsonNull.INSTANCE;
                if (!src.requiresRegistration)
                    return gsonStandard.toJsonTree(src.obj, src.obj.javaClass);
                else {
                    val obj = _gson.toJsonTree(src.obj) as JsonObject;
                    obj.addProperty("__id", src._id);

                    val methodsArray = JsonArray();
                    for (method in getV8Functions(src._class))
                        methodsArray.add(method.name);
                    obj.add("__methods", methodsArray);

                    val propsArray = JsonArray();
                    for (method in getV8Properties(src._class))
                        propsArray.add(method.name);
                    obj.add("__props", propsArray);

                    return obj;
                }
            }
            catch(ex: StackOverflowError) {
                val msg = "Recursive structure for class [${src?._class?.simpleName}], can't serialize..: ${ex.message}";
                Logger.e("V8RemoteObject", msg);
                throw IllegalArgumentException(msg);
            }
        }
    }

    companion object {
        val gsonStandard = GsonBuilder()
            .serializeNulls()
            .setPrettyPrinting()
            .create();
        private val _gson = GsonBuilder()
            .registerTypeAdapter(V8RemoteObject::class.java, Serializer())
            .create();

        private val _classV8Functions: HashMap<KClass<*>, List<KFunction<*>>> = hashMapOf();
        private val _classV8Props: HashMap<KClass<*>, List<KFunction<*>>> = hashMapOf();


        fun getV8Functions(clazz: KClass<*>): List<KFunction<*>> {
            if(!_classV8Functions.containsKey(clazz))
                _classV8Functions.put(clazz, clazz.declaredFunctions.filter { it.hasAnnotation<V8Function>() }.toList());
            return _classV8Functions.get(clazz)!!;
        }
        fun getV8Function(clazz: KClass<*>, name: String): KFunction<*> {
            val functions = getV8Functions(clazz);
            val method = functions.firstOrNull { it.name == name };
            if(method == null)
                throw IllegalArgumentException("Non-existent property ${name}");
            return method;
        }
        fun getV8Properties(clazz: KClass<*>): List<KFunction<*>> {
            if(!_classV8Props.containsKey(clazz))
                _classV8Props.put(clazz, clazz.declaredFunctions.filter { it.hasAnnotation<V8Property>() }.toList());
            return _classV8Props.get(clazz)!!;
        }
        fun getV8Property(clazz: KClass<*>, name: String): KFunction<*> {
            val props = getV8Properties(clazz);
            val method = props.firstOrNull { it.name == name };
            if(method == null)
                throw IllegalArgumentException("Non-existent property ${name}");
            return method;
        }


        fun List<V8RemoteObject?>.serialize() : String {
            return _gson.toJson(this);
        }
    }
}