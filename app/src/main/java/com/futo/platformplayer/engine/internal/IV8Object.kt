package com.futo.platformplayer.engine.internal

import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.values.V8Value


interface IV8Convertable {
    fun toV8(runtime: V8Runtime) : V8Value?;
}