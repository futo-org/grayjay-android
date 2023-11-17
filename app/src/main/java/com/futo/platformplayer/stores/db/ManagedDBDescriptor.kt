package com.futo.platformplayer.stores.db

import androidx.sqlite.db.SimpleSQLiteQuery
import com.futo.platformplayer.models.HistoryVideo
import com.futo.platformplayer.stores.db.types.DBHistory


abstract class ManagedDBDescriptor<T, I: ManagedDBIndex<T>, D: ManagedDBDatabase<T, I, DA>, DA: ManagedDBDAOBase<T, I>> {
    abstract fun dbClass(): Class<D>;
    abstract fun create(obj: T): I;

    open val ordered: String? = null;

    open fun sqlIndexOnly(tableName: String): SimpleSQLiteQuery? = null;
    open fun sqlPage(tableName: String, page: Int, length: Int): SimpleSQLiteQuery? = null;
}