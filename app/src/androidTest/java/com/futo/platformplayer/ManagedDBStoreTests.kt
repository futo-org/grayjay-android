package com.futo.platformplayer

import androidx.test.platform.app.InstrumentationRegistry
import com.futo.platformplayer.stores.db.ManagedDBDescriptor
import com.futo.platformplayer.stores.db.ManagedDBStore
import com.futo.platformplayer.testing.DBTOs
import org.junit.Assert
import org.junit.Test
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.reflect.KClass

class ManagedDBStoreTests {
    val context = InstrumentationRegistry.getInstrumentation().targetContext;

    @Test
    fun startup() {
        val store = ManagedDBStore.create("test", Descriptor())
            .load(context, true);
        store.deleteAll();

        store.shutdown();
    }

    @Test
    fun insert() {
        val store = ManagedDBStore.create("test", Descriptor())
            .load(context, true);
        store.deleteAll();

        val testObj = DBTOs.TestObject();
        createAndAssert(store, testObj);

        store.shutdown();
    }
    @Test
    fun update() {
        val store = ManagedDBStore.create("test", Descriptor())
            .load(context, true);
        store.deleteAll();

        val testObj = DBTOs.TestObject();
        val obj = createAndAssert(store, testObj);

        testObj.someStr = "Testing";
        store.update(obj.id!!, testObj);
        val obj2 = store.get(obj.id!!);
        assertIndexEquals(obj2, testObj);

        store.shutdown();
    }
    @Test
    fun delete() {
        val store = ManagedDBStore.create("test", Descriptor())
            .load(context, true);
        store.deleteAll();

        val testObj = DBTOs.TestObject();
        val obj = createAndAssert(store, testObj);
        store.delete(obj.id!!);

        Assert.assertEquals(store.count(), 0);
        Assert.assertNull(store.getOrNull(obj.id!!));

        store.shutdown();
    }

    @Test
    fun withIndex() {
        val index = ConcurrentHashMap<Any, DBTOs.TestIndex>();
        val store = ManagedDBStore.create("test", Descriptor())
            .withIndex({it.someString}, index, true)
            .load(context, true);
        store.deleteAll();

        val testObj1 = DBTOs.TestObject();
        val testObj2 = DBTOs.TestObject();
        val testObj3 = DBTOs.TestObject();
        val obj1 = createAndAssert(store, testObj1);
        val obj2 = createAndAssert(store, testObj2);
        val obj3 = createAndAssert(store, testObj3);
        Assert.assertEquals(store.count(), 3);

        Assert.assertTrue(index.containsKey(testObj1.someStr));
        Assert.assertTrue(index.containsKey(testObj2.someStr));
        Assert.assertTrue(index.containsKey(testObj3.someStr));
        Assert.assertEquals(index.size, 3);

        val oldStr = testObj1.someStr;
        testObj1.someStr = UUID.randomUUID().toString();
        store.update(obj1.id!!, testObj1);

        Assert.assertEquals(index.size, 3);
        Assert.assertFalse(index.containsKey(oldStr));
        Assert.assertTrue(index.containsKey(testObj1.someStr));
        Assert.assertTrue(index.containsKey(testObj2.someStr));
        Assert.assertTrue(index.containsKey(testObj3.someStr));

        store.delete(obj2.id!!);
        Assert.assertEquals(index.size, 2);

        Assert.assertFalse(index.containsKey(oldStr));
        Assert.assertTrue(index.containsKey(testObj1.someStr));
        Assert.assertFalse(index.containsKey(testObj2.someStr));
        Assert.assertTrue(index.containsKey(testObj3.someStr));
        store.shutdown();
    }

    @Test
    fun withUnique() {
        val index = ConcurrentHashMap<Any, DBTOs.TestIndex>();
        val store = ManagedDBStore.create("test", Descriptor())
            .withIndex({it.someString}, index, false, true)
            .load(context, true);
        store.deleteAll();

        val testObj1 = DBTOs.TestObject();
        val testObj2 = DBTOs.TestObject();
        val testObj3 = DBTOs.TestObject();
        val obj1 = createAndAssert(store, testObj1);
        val obj2 = createAndAssert(store, testObj2);

        testObj3.someStr = testObj2.someStr;
        Assert.assertEquals(store.insert(testObj3), obj2.id!!);
        Assert.assertEquals(store.count(), 2);

        store.shutdown();
    }



    private fun createAndAssert(store: ManagedDBStore<DBTOs.TestIndex, DBTOs.TestObject, DBTOs.DB, DBTOs.DBDAO>, obj: DBTOs.TestObject): DBTOs.TestIndex {
        val id = store.insert(obj);
        Assert.assertTrue(id > 0);

        val dbObj = store.get(id);
        assertIndexEquals(dbObj, obj);
        return dbObj;
    }

    private fun assertObjectEquals(obj1: DBTOs.TestObject, obj2: DBTOs.TestObject) {
        Assert.assertEquals(obj1.someStr, obj2.someStr);
        Assert.assertEquals(obj1.someNum, obj2.someNum);
    }
    private fun assertIndexEquals(obj1: DBTOs.TestIndex, obj2: DBTOs.TestObject) {
        Assert.assertEquals(obj1.someString, obj2.someStr);
        Assert.assertEquals(obj1.someNum, obj2.someNum);
        assertObjectEquals(obj1.obj, obj2);
    }


    class Descriptor: ManagedDBDescriptor<DBTOs.TestObject, DBTOs.TestIndex, DBTOs.DB, DBTOs.DBDAO>() {
        override val table_name: String = "testing";
        override fun indexClass(): KClass<DBTOs.TestIndex> = DBTOs.TestIndex::class;
        override fun dbClass(): KClass<DBTOs.DB> = DBTOs.DB::class;
        override fun create(obj: DBTOs.TestObject): DBTOs.TestIndex = DBTOs.TestIndex(obj);
    }
}