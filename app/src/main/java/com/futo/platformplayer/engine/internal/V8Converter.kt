package com.futo.platformplayer.engine.internal

import com.caoccao.javet.annotations.V8Convert
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.interop.converters.JavetObjectConverter
import com.caoccao.javet.interop.converters.JavetProxyConverter
import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.engine.V8Plugin


class V8Converter : JavetObjectConverter() {


    override fun <T : V8Value?> toV8Value(v8Runtime: V8Runtime, obj: Any?, depth: Int): T? {
        if (obj == null)
            return null;

        val value: V8Value? = super.toV8Value(v8Runtime, obj, depth)
        if (value != null && !value.isUndefined)
            return value as T;
        if (obj != null) {
            if (obj is IV8Convertable)
                return obj.toV8(v8Runtime) as T;
        }
        return null;
    }
}