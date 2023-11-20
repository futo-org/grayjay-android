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
    var obj: T? = null;
}