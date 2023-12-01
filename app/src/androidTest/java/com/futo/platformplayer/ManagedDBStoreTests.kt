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
    @Test
    fun getPage() {
        val store = ManagedDBStore.create("test", Descriptor())
            .load(context, true);
        store.deleteAll();

        val testObjs = createSequence(store, 25);

        val page1 = store.getPage(0, 10);
        val page2 = store.getPage(1, 10);
        val page3 = store.getPage(2, 10);
        Assert.assertEquals(10, page1.size);
        Assert.assertEquals(10, page2.size);
        Assert.assertEquals(5, page3.size);

        store.shutdown();
    }


    @Test
    fun query() {
        val store = ManagedDBStore.create("test", Descriptor())
            .load(context, true);
        store.deleteAll();

        val testStr = UUID.randomUUID().toString();

        val testObj1 = DBTOs.TestObject();
        val testObj2 = DBTOs.TestObject();
        val testObj3 = DBTOs.TestObject();
        val testObj4 = DBTOs.TestObject();
        testObj3.someStr = testStr;
        testObj4.someStr = testStr;
        val obj1 = createAndAssert(store, testObj1);
        val obj2 = createAndAssert(store, testObj2);
        val obj3 = createAndAssert(store, testObj3);
        val obj4 = createAndAssert(store, testObj4);

        val results = store.query(DBTOs.TestIndex::someString, testStr);

        Assert.assertEquals(2, results.size);
        for(result in results) {
            if(result.someNum == obj3.someNum)
                assertIndexEquals(obj3, result);
            else
                assertIndexEquals(obj4, result);
        }
        store.shutdown();
    }
    @Test
    fun queryPage() {
        val index = ConcurrentHashMap<Any, DBTOs.TestIndex>();
        val store = ManagedDBStore.create("test", Descriptor())
            .withIndex({ it.someNum },  index)
            .load(context, true);
        store.deleteAll();

        val testStr = UUID.randomUUID().toString();

        val testResults = createSequence(store, 40, { i, testObject ->
            if(i % 2 == 0)
                testObject.someStr = testStr;
        });
        val page1 = store.queryPage(DBTOs.TestIndex::someString, testStr, 0,10);
        val page2 = store.queryPage(DBTOs.TestIndex::someString, testStr, 1,10);
        val page3 = store.queryPage(DBTOs.TestIndex::someString, testStr, 2,10);

        Assert.assertEquals(10, page1.size);
        Assert.assertEquals(10, page2.size);
        Assert.assertEquals(0, page3.size);


        store.shutdown();
    }
    @Test
    fun queryPager() {
        val testStr = UUID.randomUUID().toString();
        testQuery(100, { i, testObject ->
            if(i % 2 == 0)
                testObject.someStr = testStr;
        }) {
            val pager = it.queryPager(DBTOs.TestIndex::someString, testStr, 10);

            val items = pager.getResults().toMutableList();
            while(pager.hasMorePages()) {
                pager.nextPage();
                items.addAll(pager.getResults());
            }
            Assert.assertEquals(50, items.size);
            for(i in 0 until 50) {
                val k = i * 2;
                Assert.assertEquals(k, items[i].someNum);
            }
        }
    }



    @Test
    fun queryLike() {
        val testStr = UUID.randomUUID().toString();
        val testStrLike = testStr.substring(0, 8) + "Testing" + testStr.substring(8, testStr.length);
        testQuery(100, { i, testObject ->
            if(i % 2 == 0)
                testObject.someStr = testStrLike;
        }) {
            val results = it.queryLike(DBTOs.TestIndex::someString, "%Testing%");

            Assert.assertEquals(50, results.size);
        }
    }
    @Test
    fun queryLikePager() {
        val testStr = UUID.randomUUID().toString();
        val testStrLike = testStr.substring(0, 8) + "Testing" + testStr.substring(8, testStr.length);
        testQuery(100, { i, testObject ->
            if(i % 2 == 0)
                testObject.someStr = testStrLike;

        }) {
            val pager = it.queryLikePager(DBTOs.TestIndex::someString, "%Testing%", 10);
            val items = pager.getResults().toMutableList();
            while(pager.hasMorePages()) {
                pager.nextPage();
                items.addAll(pager.getResults());
            }
            Assert.assertEquals(50, items.size);
            for(i in 0 until 50) {
                val k = i * 2;
                Assert.assertEquals(k, items[i].someNum);
            }
        }
    }

    @Test
    fun queryGreater() {
        testQuery(100, { i, testObject ->
            testObject.someNum = i;
        }) {
            val results = it.queryGreater(DBTOs.TestIndex::someNum, 51);
            Assert.assertEquals(48, results.size);
        }
    }
    @Test
    fun querySmaller() {
        testQuery(100, { i, testObject ->
            testObject.someNum = i;
        }) {
            val results = it.querySmaller(DBTOs.TestIndex::someNum, 30);
            Assert.assertEquals(30, results.size);
        }
    }
    @Test
    fun queryBetween() {
        testQuery(100, { i, testObject ->
            testObject.someNum = i;
        }) {
            val results = it.queryBetween(DBTOs.TestIndex::someNum, 30, 65);
            Assert.assertEquals(34, results.size);
        }
    }


    private fun testQuery(items: Int, modifier: (Int, DBTOs.TestObject)->Unit, testing: (ManagedDBStore<DBTOs.TestIndex, DBTOs.TestObject, DBTOs.DB, DBTOs.DBDAO>)->Unit) {
        val store = ManagedDBStore.create("test", Descriptor())
            .load(context, true);
        store.deleteAll();
        createSequence(store, items, modifier);
        try {
            testing(store);
        }
        finally {
            store.shutdown();
        }
    }


    private fun createSequence(store: ManagedDBStore<DBTOs.TestIndex, DBTOs.TestObject, DBTOs.DB, DBTOs.DBDAO>, count: Int, modifier: ((Int, DBTOs.TestObject)->Unit)? = null): List<DBTOs.TestIndex> {
        val list = mutableListOf<DBTOs.TestIndex>();
        for(i in 0 until count) {
            val obj = DBTOs.TestObject();
            obj.someNum = i;
            modifier?.invoke(i, obj);
            list.add(createAndAssert(store, obj));
        }
        return list;
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
    private fun assertIndexEquals(obj1: DBTOs.TestIndex, obj2: DBTOs.TestIndex) {
        Assert.assertEquals(obj1.someString, obj2.someString);
        Assert.assertEquals(obj1.someNum, obj2.someNum);
        assertIndexEquals(obj1, obj2.obj);
    }


    class Descriptor: ManagedDBDescriptor<DBTOs.TestObject, DBTOs.TestIndex, DBTOs.DB, DBTOs.DBDAO>() {
        override val table_name: String = "testing";
        override fun indexClass(): KClass<DBTOs.TestIndex> = DBTOs.TestIndex::class;
        override fun dbClass(): KClass<DBTOs.DB> = DBTOs.DB::class;
        override fun create(obj: DBTOs.TestObject): DBTOs.TestIndex = DBTOs.TestIndex(obj);
    }
}