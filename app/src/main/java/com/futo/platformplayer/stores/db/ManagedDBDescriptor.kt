package com.futo.platformplayer.stores.db

import kotlin.reflect.KClass


abstract class ManagedDBDescriptor<T, I: ManagedDBIndex<T>, D: ManagedDBDatabase<T, I, DA>, DA: ManagedDBDAOBase<T, I>> {
    abstract val table_name: String;
    abstract fun dbClass(): KClass<D>;
    abstract fun create(obj: T): I;

    abstract fun indexClass(): KClass<I>;
}