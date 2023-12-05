package com.futo.platformplayer.stores.db

import androidx.room.RoomDatabase

abstract class ManagedDBDatabase<T, I: ManagedDBIndex<T>, D: ManagedDBDAOBase<T, I>>: RoomDatabase() {
    abstract fun base(): D;
}