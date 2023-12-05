package com.futo.platformplayer.stores.db

import androidx.room.ColumnInfo
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.futo.platformplayer.api.media.Serializer

open class ManagedDBIndex<T> {
    @ColumnIndex
    @PrimaryKey(true)
    open var id: Long? = null;
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    var serialized: ByteArray? = null;

    @Ignore
    private var _obj: T? = null;
    @Ignore
    var isCorrupted: Boolean = false;

    @get:Ignore
    val obj: T get() = _obj ?: throw IllegalStateException("Attempted to access serialized object on a index-only instance");

    @get:Ignore
    val objOrNull: T? get() = _obj;

    fun setInstance(obj: T) {
        this._obj = obj;
    }
}