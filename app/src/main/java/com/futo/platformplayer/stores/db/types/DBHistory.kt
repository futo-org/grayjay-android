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
import com.futo.platformplayer.stores.db.ManagedDBDAOBase
import com.futo.platformplayer.stores.db.ManagedDBDatabase
import com.futo.platformplayer.stores.db.ManagedDBDescriptor
import com.futo.platformplayer.stores.db.ManagedDBIndex
import com.futo.platformplayer.stores.db.ManagedDBStore
import kotlin.reflect.KType

class DBHistory {
    companion object {
        const val TABLE_NAME = "history";
    }

    @Dao
    interface DBDAO: ManagedDBDAOBase<HistoryVideo, Index> {}
    @Database(entities = [Index::class], version = 2)
    abstract class DB: ManagedDBDatabase<HistoryVideo, Index, DBDAO>() {
        abstract override fun base(): DBDAO;
    }

    class Descriptor: ManagedDBDescriptor<HistoryVideo, Index, DB, DBDAO>() {
        override fun create(obj: HistoryVideo): Index = Index(obj);
        override fun dbClass(): Class<DB> = DB::class.java;

        //Optional
        override fun sqlIndexOnly(tableName: String): SimpleSQLiteQuery = SimpleSQLiteQuery("SELECT id, url, position, date FROM $TABLE_NAME");
        override fun sqlPage(tableName: String, page: Int, length: Int): SimpleSQLiteQuery = SimpleSQLiteQuery("SELECT * FROM $TABLE_NAME ORDER BY date DESC, id DESC LIMIT ? OFFSET ?", arrayOf(length, page * length));
    }

    @Entity(TABLE_NAME)
    class Index: ManagedDBIndex<HistoryVideo> {
        @PrimaryKey(true)
        override var id: Long? = null;
        @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
        override var serialized: ByteArray? = null;

        @Ignore
        override var obj: HistoryVideo? = null;

        var url: String;
        var position: Long;
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