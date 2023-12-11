package com.futo.platformplayer.stores.db

import androidx.room.Dao

@Dao
interface ManagedDBIndexOnly<T, I: ManagedDBIndex<T>> {
    fun getIndex(): List<I>;
}