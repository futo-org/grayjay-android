package com.futo.platformplayer.stores.db.types

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.futo.platformplayer.api.media.models.video.SerializedPlatformContent
import com.futo.platformplayer.models.HistoryVideo
import com.futo.platformplayer.stores.db.ColumnIndex
import com.futo.platformplayer.stores.db.ColumnOrdered
import com.futo.platformplayer.stores.db.ManagedDBDAOBase
import com.futo.platformplayer.stores.db.ManagedDBDatabase
import com.futo.platformplayer.stores.db.ManagedDBDescriptor
import com.futo.platformplayer.stores.db.ManagedDBIndex
import java.time.OffsetDateTime
import kotlin.reflect.KClass

class DBChannelCache {
    companion object {
        const val TABLE_NAME = "feed_cache";
    }


    //These classes solely exist for bounding generics for type erasure
    @Dao
    interface DBDAO: ManagedDBDAOBase<SerializedPlatformContent, Index> {}
    @Database(entities = [Index::class], version = 2)
    abstract class DB: ManagedDBDatabase<SerializedPlatformContent, Index, DBDAO>() {
        abstract override fun base(): DBDAO;
    }

    class Descriptor: ManagedDBDescriptor<SerializedPlatformContent, Index, DB, DBDAO>() {
        override val table_name: String = TABLE_NAME;
        override fun create(obj: SerializedPlatformContent): Index = Index(obj);
        override fun dbClass(): KClass<DB> = DB::class;
        override fun indexClass(): KClass<Index> = Index::class;
    }

    class Index: ManagedDBIndex<SerializedPlatformContent> {
        @ColumnIndex
        @PrimaryKey(true)
        @ColumnOrdered(1)
        override var id: Long? = null;

        @ColumnIndex
        var url: String? = null;
        @ColumnIndex
        var channelUrl: String? = null;

        @ColumnIndex
        @ColumnOrdered(0)
        var datetime: Long? = null;


        constructor() {}
        constructor(sCache: SerializedPlatformContent) {
            id = null;
            serialized = null;
            url = sCache.url;
            channelUrl = sCache.author.url;
            datetime = sCache.datetime?.toEpochSecond();
        }
    }
}