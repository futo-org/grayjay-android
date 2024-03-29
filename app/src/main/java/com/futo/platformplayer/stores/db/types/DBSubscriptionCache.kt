package com.futo.platformplayer.stores.db.types

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.futo.platformplayer.api.media.models.video.SerializedPlatformContent
import com.futo.platformplayer.stores.db.ColumnIndex
import com.futo.platformplayer.stores.db.ColumnOrdered
import com.futo.platformplayer.stores.db.ManagedDBDAOBase
import com.futo.platformplayer.stores.db.ManagedDBDatabase
import com.futo.platformplayer.stores.db.ManagedDBDescriptor
import com.futo.platformplayer.stores.db.ManagedDBIndex
import kotlin.reflect.KClass

class DBSubscriptionCache {
    companion object {
        const val TABLE_NAME = "subscription_cache";
    }


    //These classes solely exist for bounding generics for type erasure
    @Dao
    interface DBDAO: ManagedDBDAOBase<SerializedPlatformContent, Index> {}
    @Database(entities = [Index::class], version = 5)
    abstract class DB: ManagedDBDatabase<SerializedPlatformContent, Index, DBDAO>() {
        abstract override fun base(): DBDAO;
    }

    class Descriptor: ManagedDBDescriptor<SerializedPlatformContent, Index, DB, DBDAO>() {
        override val table_name: String = TABLE_NAME;
        override fun create(obj: SerializedPlatformContent): Index = Index(obj);
        override fun dbClass(): KClass<DB> = DB::class;
        override fun indexClass(): KClass<Index> = Index::class;
    }

    @Entity(TABLE_NAME, indices = [
        androidx.room.Index(value = ["url"]),
        androidx.room.Index(value = ["channelUrl"]),
        androidx.room.Index(value = ["datetime"], orders = [androidx.room.Index.Order.DESC])
    ])
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
        @ColumnOrdered(0, true)
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