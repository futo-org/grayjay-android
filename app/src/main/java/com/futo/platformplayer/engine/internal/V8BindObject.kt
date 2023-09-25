package com.futo.platformplayer.engine.internal

import com.caoccao.javet.annotations.V8Function
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.reference.V8ValueObject

open class V8BindObject : IV8Convertable {
    protected var _runtimeObj: V8ValueObject? = null;
    protected var _isDisposed: Boolean = false
        private set;


    override fun toV8(runtime: V8Runtime): V8Value? {
        synchronized(this) {
            if(_runtimeObj != null)
                return _runtimeObj;

            val v8Obj = runtime.createV8ValueObject();
            v8Obj.bind(this);
            _runtimeObj = v8Obj;
            return v8Obj;
        }
    }

    @V8Function
    open fun dispose() {
        if(!_isDisposed) {
            //_runtimeObj?.v8Runtime?.v8Internal?.removeReference(_runtimeObj);
            //_isDisposed = true;
        }
    }
}