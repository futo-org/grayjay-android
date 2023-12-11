package com.futo.platformplayer.stores.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery


@Dao
interface ManagedDBDAOBase<T, I: ManagedDBIndex<T>> {

    @RawQuery
    fun get(query: SupportSQLiteQuery): I;
    @RawQuery
    fun getNullable(query: SupportSQLiteQuery): I?;
    @RawQuery
    fun getMultiple(query: SupportSQLiteQuery): List<I>;

    @RawQuery
    fun action(query: SupportSQLiteQuery): Int

    @Insert
    fun insert(index: I): Long;
    @Insert
    fun insertAll(vararg indexes: I)

    @Update
    fun update(index: I);

    @Delete
    fun delete(index: I);
}