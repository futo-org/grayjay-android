package com.futo.platformplayer.stores.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Update

@Dao
interface ManagedDBContextPaged<T, I: ManagedDBIndex<T>> {
    fun getPaged(page: Int, pageSize: Int): List<I>;
}