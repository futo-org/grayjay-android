package com.futo.platformplayer.stores.db

import androidx.room.Dao

@Dao
interface ManagedDBContextPaged<T, I: ManagedDBIndex<T>> {
    fun getPaged(page: Int, pageSize: Int): List<I>;
}