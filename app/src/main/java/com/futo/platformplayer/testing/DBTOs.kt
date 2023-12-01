package com.futo.platformplayer.testing

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import com.futo.platformplayer.stores.db.ColumnIndex
import com.futo.platformplayer.stores.db.ColumnOrdered
import com.futo.platformplayer.stores.db.ManagedDBDAOBase
import com.futo.platformplayer.stores.db.ManagedDBDatabase
import com.futo.platformplayer.stores.db.ManagedDBIndex
import kotlinx.serialization.Serializable
import java.util.Random
import java.util.UUID

class DBTOs {
    @Dao
    interface DBDAO: ManagedDBDAOBase<TestObject, TestIndex> {}
    @Database(entities = [TestIndex::class], version = 3)
    abstract class DB: ManagedDBDatabase<TestObject, TestIndex, DBDAO>() {
        abstract override fun base(): DBDAO;
    }


    @Entity("testing")
    class TestIndex(): ManagedDBIndex<TestObject>() {

        @ColumnIndex
        var someString: String = "";
        @ColumnIndex
        @ColumnOrdered(0)
        var someNum: Int = 0;

        constructor(obj: TestObject, customInt: Int? = null) : this() {
            someString = obj.someStr;
            someNum = customInt ?: obj.someNum;
        }
    }
    @Serializable
    class TestObject {
        var someStr = UUID.randomUUID().toString();
        var someNum = random.nextInt();
    }

    companion object {
        val random = Random();
    }
}