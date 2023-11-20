package com.futo.platformplayer.stores.db.types

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.sqlite.db.SimpleSQLiteQuery
import com.futo.platformplayer.api.media.models.video.SerializedPlatformContent
import com.futo.platformplayer.models.HistoryVideo
import com.futo.platformplayer.stores.db.ColumnIndex
import com.futo.platformplayer.stores.db.ColumnOrdered
import com.futo.platformplayer.stores.db.ManagedDBDAOBase
import com.futo.platformplayer.stores.db.ManagedDBDatabase
import com.futo.platformplayer.stores.db.ManagedDBDescriptor
import com.futo.platformplayer.stores.db.ManagedDBIndex
import com.futo.platformplayer.stores.db.ManagedDBStore
import kotlin.reflect.KClass
import kotlin.reflect.KType

class DBHistory {
    companion object {
        const val TABLE_NAME = "history";
    }

    //These classes solely exist for bounding generics for type erasure
    @Dao
    interface DBDAO: ManagedDBDAOBase<HistoryVideo, Index> {}
    @Database(entities = [Index::class], version = 2)
    abstract class DB: ManagedDBDatabase<HistoryVideo, Index, DBDAO>() {
        abstract override fun base(): DBDAO;
    }

    class Descriptor: ManagedDBDescriptor<HistoryVideo, Index, DB, DBDAO>() {
        override val table_name: String = TABLE_NAME;
        override fun create(obj: HistoryVideo): Index = Index(obj);
        override fun dbClass(): KClass<DB> = DB::class;
        override fun indexClass(): KClass<Index> = Index::class;
    }

    @Entity(TABLE_NAME)
    class Index: ManagedDBIndex<HistoryVideo> {
        @PrimaryKey(true)
        @ColumnOrdered(1)
        @ColumnIndex
        override var id: Long? = null;

        @ColumnIndex
        var url: String;
        @ColumnIndex
        var position: Long;
        @ColumnIndex
        @ColumnOrdered(0, true)
        var date: Long;

        constructor() {
            url = "";
            position = 0;
            date = 0;
        }
        constructor(historyVideo: HistoryVideo) {
            id = null;
            serialized = null;
            url = historyVideo.video.url;
            position = historyVideo.position;
            date = historyVideo.date.toEpochSecond();
            obj = historyVideo;
        }
    }
}