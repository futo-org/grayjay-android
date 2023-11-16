package com.futo.platformplayer.stores.db

import com.futo.platformplayer.assume
import com.futo.platformplayer.stores.v2.ManagedStore
import com.futo.platformplayer.stores.v2.ReconstructStore
import com.futo.platformplayer.stores.v2.StoreSerializer
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KType

class ManagedDBStore<I, T> {
    private val _class: KType;
    private val _name: String;
    private val _serializer: StoreSerializer<T>;


    private var _isLoaded = false;

    private var _withUnique: ((I) -> Any)? = null;

    val className: String? get() = _class.classifier?.assume<KClass<*>>()?.simpleName;

    val name: String;

    constructor(name: String, clazz: KType, serializer: StoreSerializer<T>, niceName: String? = null) {
        _name = name;
        this.name = niceName ?: name.let {
            if(it.isNotEmpty())
                return@let it[0].uppercase() + it.substring(1);
            return@let name;
        };
        _serializer = serializer;
        _class = clazz;
    }

    fun load() {
        throw NotImplementedError();
        _isLoaded = true;
    }

}