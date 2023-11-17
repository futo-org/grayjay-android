package com.futo.platformplayer.stores.db

import androidx.room.ColumnInfo
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.futo.platformplayer.api.media.Serializer

interface ManagedDBIndex<T> {
    var id: Long?
    var serialized: ByteArray?

    @get:Ignore
    var obj: T?;
}