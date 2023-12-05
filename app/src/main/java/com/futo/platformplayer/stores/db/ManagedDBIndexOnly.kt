package com.futo.platformplayer.stores.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Update

@Dao
interface ManagedDBIndexOnly<T, I: ManagedDBIndex<T>> {
    fun getIndex(): List<I>;
}