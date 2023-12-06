package com.futo.platformplayer.stores.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import com.futo.platformplayer.api.media.structures.AdhocPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.assume
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.stores.v2.JsonStoreSerializer
import com.futo.platformplayer.stores.v2.StoreSerializer
import kotlinx.serialization.KSerializer
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

class ManagedDBStore<I: ManagedDBIndex<T>, T, D: ManagedDBDatabase<T, I, DA>, DA: ManagedDBDAOBase<T, I>> {
    private val _class: KType;
    private val _name: String;
    private val _serializer: StoreSerializer<T>;

    private var _db: ManagedDBDatabase<T, I, *>? = null;
    private var _dbDaoBase: ManagedDBDAOBase<T, I>? = null;
    val dbDaoBase: ManagedDBDAOBase<T, I> get() = _dbDaoBase ?: throw IllegalStateException("Not initialized db [${name}]");

    val descriptor: ManagedDBDescriptor<T, I, D, DA>;

    private val _columnInfo: List<ColumnMetadata>;

    private val _sqlGet: (Long)-> SimpleSQLiteQuery;
    private val _sqlGetIndex: (Long)-> SimpleSQLiteQuery;
    private val _sqlGetAll: (LongArray)-> SimpleSQLiteQuery;
    private val _sqlAll: SimpleSQLiteQuery;
    private val _sqlCount: SimpleSQLiteQuery;
    private val _sqlDeleteAll: SimpleSQLiteQuery;
    private val _sqlDeleteById: (Long) -> SimpleSQLiteQuery;
    private var _sqlIndexed: SimpleSQLiteQuery? = null;
    private var _sqlPage: ((Int, Int) -> SimpleSQLiteQuery)? = null;

    val className: String? get() = _class.classifier?.assume<KClass<*>>()?.simpleName;

    val name: String;

    private val _indexes: ArrayList<IndexDescriptor<I>> = arrayListOf();
    private val _indexCollection = ConcurrentHashMap<Long, I>();

    private var _withUnique: Pair<(I)->Any, ConcurrentMap<Any, I>>? = null;
    private val _orderSQL: String?;

    constructor(name: String, descriptor: ManagedDBDescriptor<T, I, D, DA>, clazz: KType, serializer: StoreSerializer<T>, niceName: String? = null) {
        this.descriptor = descriptor;
        _name = name;
        this.name = niceName ?: name.let {
            if(it.isNotEmpty())
                return@let it[0].uppercase() + it.substring(1);
            return@let name;
        };
        _serializer = serializer;
        _class = clazz;
        _columnInfo = this.descriptor.indexClass().memberProperties
            .filter { it.hasAnnotation<ColumnIndex>() && it.name != "serialized" }
            .map { ColumnMetadata(it.javaField!!, it.findAnnotation<ColumnIndex>()!!, it.findAnnotation<ColumnOrdered>()) };

        val indexColumnNames = _columnInfo.map { it.name };

        val orderedColumns = _columnInfo.filter { it.ordered != null }.sortedBy { it.ordered!!.priority };
        _orderSQL = if(orderedColumns.size > 0)
            " ORDER BY " + orderedColumns.map { "${it.name} ${if(it.ordered!!.descending) "DESC" else "ASC"}" }.joinToString(", ");
        else "";

        _sqlGet = { SimpleSQLiteQuery("SELECT * FROM ${this.descriptor.table_name} WHERE id = ?", arrayOf(it)) };
        _sqlGetIndex = { SimpleSQLiteQuery("SELECT ${indexColumnNames.joinToString(", ")} FROM ${this.descriptor.table_name} WHERE id = ?", arrayOf(it)) };
        _sqlGetAll = { SimpleSQLiteQuery("SELECT * FROM ${this.descriptor.table_name} WHERE id IN (?)", arrayOf(it)) };
        _sqlAll = SimpleSQLiteQuery("SELECT * FROM ${this.descriptor.table_name} ${_orderSQL}");
        _sqlCount = SimpleSQLiteQuery("SELECT COUNT(id) FROM ${this.descriptor.table_name}");
        _sqlDeleteAll = SimpleSQLiteQuery("DELETE FROM ${this.descriptor.table_name}");
        _sqlDeleteById = { id -> SimpleSQLiteQuery("DELETE FROM ${this.descriptor.table_name} WHERE id = :id", arrayOf(id)) };
        _sqlIndexed = SimpleSQLiteQuery("SELECT ${indexColumnNames.joinToString(", ")} FROM ${this.descriptor.table_name}");

        if(orderedColumns.size > 0) {
            _sqlPage = { page, length ->
                SimpleSQLiteQuery("SELECT * FROM ${this.descriptor.table_name} ${_orderSQL} LIMIT ? OFFSET ?", arrayOf(length, page * length));
            }
        }
    }

    fun withIndex(keySelector: (I)->Any, indexContainer: ConcurrentMap<Any, I>, allowChange: Boolean = false, withUnique: Boolean = false): ManagedDBStore<I, T, D, DA> {
        if(_sqlIndexed == null)
            throw IllegalStateException("Can only create indexes if sqlIndexOnly is implemented");
        _indexes.add(IndexDescriptor(keySelector, indexContainer, allowChange));

        if(withUnique)
            withUnique(keySelector, indexContainer);

        return this;
    }
    fun withUnique(keySelector: (I)->Any, indexContainer: ConcurrentMap<Any, I>): ManagedDBStore<I, T, D, DA> {
        if(_withUnique != null)
            throw IllegalStateException("Only 1 unique property is allowed");
        _withUnique = Pair(keySelector, indexContainer);

        return this;
    }

    fun load(context: Context? = null, inMemory: Boolean = false): ManagedDBStore<I, T, D, DA> {
        _db = (if(!inMemory)
                    Room.databaseBuilder(context ?: StateApp.instance.context, descriptor.dbClass().java, _name)
                else
                    Room.inMemoryDatabaseBuilder(context ?: StateApp.instance.context, descriptor.dbClass().java))
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()
        _dbDaoBase = _db!!.base() as ManagedDBDAOBase<T, I>;
        if(_indexes.any()) {
            val allItems = _dbDaoBase!!.getMultiple(_sqlIndexed!!);
            for(index in _indexes)
                index.collection.putAll(allItems.associateBy(index.keySelector));
        }

        return this;
    }
    fun shutdown() {
        val db = _db;
        _db = null;
        _dbDaoBase = null;
        db?.close();
    }

    fun getUnique(obj: I): I? {
        if(_withUnique == null)
            throw IllegalStateException("Unique is not configured for ${name}");
        val key = _withUnique!!.first.invoke(obj);
        return _withUnique!!.second[key];
    }
    fun isUnique(obj: I): Boolean {
        if(_withUnique == null)
            throw IllegalStateException("Unique is not configured for ${name}");
        val key = _withUnique!!.first.invoke(obj);
        return !_withUnique!!.second.containsKey(key);
    }

    fun count(): Int {
        return dbDaoBase.action(_sqlCount);
    }

    fun insert(obj: T): Long {
        val newIndex = descriptor.create(obj);

        if(_withUnique != null) {
            val unique = getUnique(newIndex);
            if (unique != null)
                return unique.id!!;
        }

        newIndex.serialized = serialize(obj);
        newIndex.id = dbDaoBase.insert(newIndex);
        newIndex.serialized = null;


        if(!_indexes.isEmpty()) {
            for (index in _indexes) {
                val key = index.keySelector(newIndex);
                index.collection.put(key, newIndex);
            }
        }
        return newIndex.id!!;
    }
    fun update(id: Long, obj: T) {
        val existing = if(_indexes.any { it.checkChange }) _dbDaoBase!!.getNullable(_sqlGetIndex(id)) else null

        val newIndex = descriptor.create(obj);
        newIndex.id = id;
        newIndex.serialized = serialize(obj);
        dbDaoBase.update(newIndex);
        newIndex.serialized = null;

        if(!_indexes.isEmpty()) {
            for (index in _indexes) {
                val key = index.keySelector(newIndex);
                if(index.checkChange && existing != null) {
                    val keyExisting = index.keySelector(existing);
                    if(keyExisting != key)
                        index.collection.remove(keyExisting);
                }
                index.collection.put(key, newIndex);
            }
        }
    }

    fun getAllIndexes(): List<I> {
        if(_sqlIndexed == null)
            throw IllegalStateException("Can only create indexes if sqlIndexOnly is implemented");
        return dbDaoBase.getMultiple(_sqlIndexed!!);
    }

    fun getAllObjects(): List<T> = convertObjects(getAll());
    fun getAll(): List<I> {
        return deserializeIndexes(dbDaoBase.getMultiple(_sqlAll));
    }

    fun getObject(id: Long) = get(id).obj!!;
    fun get(id: Long): I {
        return deserializeIndex(dbDaoBase.get(_sqlGet(id)));
    }
    fun getOrNull(id: Long): I? {
        val result = dbDaoBase.getNullable(_sqlGet(id));
        if(result == null)
            return null;
        return deserializeIndex(result);
    }
    fun getIndexOnlyOrNull(id: Long): I? {
        return dbDaoBase.get(_sqlGetIndex(id));
    }

    fun getAllObjects(vararg id: Long): List<T> = getAll(*id).map { it.obj!! };
    fun getAll(vararg id: Long): List<I> {
        return deserializeIndexes(dbDaoBase.getMultiple(_sqlGetAll(id)));
    }

    fun query(field: KProperty<*>, obj: Any): List<I> = query(validateFieldName(field), obj);
    fun query(field: String, obj: Any): List<I> {
        val queryStr = "SELECT * FROM ${descriptor.table_name} WHERE ${field} = ?";
        val query = SimpleSQLiteQuery(queryStr, arrayOf(obj));
        return deserializeIndexes(dbDaoBase.getMultiple(query));
    }
    fun queryLike(field: KProperty<*>, obj: String): List<I> = queryLike(validateFieldName(field), obj);
    fun queryLike(field: String, obj: String): List<I> {
        val queryStr = "SELECT * FROM ${descriptor.table_name} WHERE ${field} LIKE ?";
        val query = SimpleSQLiteQuery(queryStr, arrayOf(obj));
        return deserializeIndexes(dbDaoBase.getMultiple(query));
    }
    fun queryGreater(field: KProperty<*>, obj: Any): List<I> = queryGreater(validateFieldName(field), obj);
    fun queryGreater(field: String, obj: Any): List<I> {
        val queryStr = "SELECT * FROM ${descriptor.table_name} WHERE ${field} > ?";
        val query = SimpleSQLiteQuery(queryStr, arrayOf(obj));
        return deserializeIndexes(dbDaoBase.getMultiple(query));
    }
    fun querySmaller(field: KProperty<*>, obj: Any): List<I> = querySmaller(validateFieldName(field), obj);
    fun querySmaller(field: String, obj: Any): List<I> {
        val queryStr = "SELECT * FROM ${descriptor.table_name} WHERE ${field} < ?";
        val query = SimpleSQLiteQuery(queryStr, arrayOf(obj));
        return deserializeIndexes(dbDaoBase.getMultiple(query));
    }
    fun queryBetween(field: KProperty<*>, greaterThan: Any, smallerThan: Any): List<I> = queryBetween(validateFieldName(field), greaterThan, smallerThan);
    fun queryBetween(field: String, greaterThan: Any, smallerThan: Any): List<I> {
        val queryStr = "SELECT * FROM ${descriptor.table_name} WHERE ${field} > ? AND ${field} < ?";
        val query = SimpleSQLiteQuery(queryStr, arrayOf(greaterThan, smallerThan));
        return deserializeIndexes(dbDaoBase.getMultiple(query));
    }

    //Query Pages
    fun queryPage(field: KProperty<*>, obj: Any, page: Int, pageSize: Int): List<I> = queryPage(validateFieldName(field), obj, page, pageSize);
    fun queryPage(field: String, obj: Any, page: Int, pageSize: Int): List<I> {
        val queryStr = "SELECT * FROM ${descriptor.table_name} WHERE ${field} = ? ${_orderSQL} LIMIT ? OFFSET ?";
        val query = SimpleSQLiteQuery(queryStr, arrayOf(obj, pageSize, page * pageSize));
        return deserializeIndexes(dbDaoBase.getMultiple(query));
    }


    fun queryLikePage(field: KProperty<*>, obj: String, page: Int, pageSize: Int): List<I> = queryLikePage(validateFieldName(field), obj, page, pageSize);
    fun queryLikePage(field: String, obj: String, page: Int, pageSize: Int): List<I> {
        val queryStr = "SELECT * FROM ${descriptor.table_name} WHERE ${field} LIKE ? ${_orderSQL} LIMIT ? OFFSET ?";
        val query = SimpleSQLiteQuery(queryStr, arrayOf(obj, pageSize, page * pageSize));
        return deserializeIndexes(dbDaoBase.getMultiple(query));
    }
    fun queryLikeObjectPage(field: String, obj: String, page: Int, pageSize: Int): List<T> {
        return convertObjects(queryLikePage(field, obj, page, pageSize));
    }


    //Query Page Objects
    fun queryPageObjects(field: String, obj: Any, page: Int, pageSize: Int): List<T> = convertObjects(queryPage(field, obj, page, pageSize));
    fun queryPageObjects(field: KProperty<*>, obj: Any, page: Int, pageSize: Int): List<T> = queryPageObjects(validateFieldName(field), obj, page, pageSize);

    //Query Pager
    fun queryPager(field: KProperty<*>, obj: Any, pageSize: Int): IPager<I> = queryPager(validateFieldName(field), obj, pageSize);
    fun queryPager(field: String, obj: Any, pageSize: Int): IPager<I> {
        return AdhocPager({
            Logger.i("ManagedDBStore", "Next Page [query: ${obj}](${it}) ${pageSize}");
           queryPage(field, obj, it - 1, pageSize);
        });
    }


    fun queryInPage(field: KProperty<*>, obj: List<String>, page: Int, pageSize: Int): List<I> = queryInPage(validateFieldName(field), obj, page, pageSize);
    fun queryInPage(field: String, obj: List<String>, page: Int, pageSize: Int): List<I> {
        val queryStr = "SELECT * FROM ${descriptor.table_name} WHERE ${field} IN (${obj.joinToString(",") { "?" }}) ${_orderSQL} LIMIT ? OFFSET ?";
        val query = SimpleSQLiteQuery(queryStr, (obj + arrayOf(pageSize, page * pageSize)).toTypedArray());
        return deserializeIndexes(dbDaoBase.getMultiple(query));
    }
    fun queryInObjectPage(field: String, obj: List<String>, page: Int, pageSize: Int): List<T> {
        return convertObjects(queryInPage(field, obj, page, pageSize));
    }
    fun queryInPager(field: KProperty<*>, obj: List<String>, pageSize: Int): IPager<I> = queryInPager(validateFieldName(field), obj, pageSize);
    fun queryInPager(field: String, obj: List<String>, pageSize: Int): IPager<I> {
        return AdhocPager({
            Logger.i("ManagedDBStore", "Next Page [query: ${obj}](${it}) ${pageSize}");
            queryInPage(field, obj, it - 1, pageSize);
        });
    }
    fun queryInObjectPager(field: KProperty<*>, obj: List<String>, pageSize: Int): IPager<T> = queryInObjectPager(validateFieldName(field), obj, pageSize);
    fun queryInObjectPager(field: String, obj: List<String>, pageSize: Int): IPager<T> {
        return AdhocPager({
            Logger.i("ManagedDBStore", "Next Page [query: ${obj}](${it}) ${pageSize}");
            queryInObjectPage(field, obj, it - 1, pageSize);
        });
    }

    fun <X> queryInPager(field: KProperty<*>, obj: List<String>, pageSize: Int, convert: (I)->X): IPager<X> = queryInPager(validateFieldName(field), obj, pageSize, convert);
    fun <X> queryInPager(field: String, obj: List<String>, pageSize: Int, convert: (I)->X): IPager<X> {
        return AdhocPager({
            queryInPage(field, obj, it - 1, pageSize).map(convert);
        });
    }

    fun queryLikePager(field: KProperty<*>, obj: String, pageSize: Int): IPager<I> = queryLikePager(validateFieldName(field), obj, pageSize);
    fun queryLikePager(field: String, obj: String, pageSize: Int): IPager<I> {
        return AdhocPager({
            Logger.i("ManagedDBStore", "Next Page [query: ${obj}](${it}) ${pageSize}");
            queryLikePage(field, obj, it - 1, pageSize);
        });
    }
    fun queryLikeObjectPager(field: KProperty<*>, obj: String, pageSize: Int): IPager<T> = queryLikeObjectPager(validateFieldName(field), obj, pageSize);
    fun queryLikeObjectPager(field: String, obj: String, pageSize: Int): IPager<T> {
        return AdhocPager({
            Logger.i("ManagedDBStore", "Next Page [query: ${obj}](${it}) ${pageSize}");
            queryLikeObjectPage(field, obj, it - 1, pageSize);
        });
    }

    //Query Pager with convert
    fun <X> queryPager(field: KProperty<*>, obj: Any, pageSize: Int, convert: (I)->X): IPager<X> = queryPager(validateFieldName(field), obj, pageSize, convert);
    fun <X> queryPager(field: String, obj: Any, pageSize: Int, convert: (I)->X): IPager<X> {
        return AdhocPager({
            queryPage(field, obj, it - 1, pageSize).map(convert);
        });
    }

    //Query Object Pager
    fun queryObjectPager(field: KProperty<*>, obj: Any, pageSize: Int): IPager<T> = queryObjectPager(validateFieldName(field), obj, pageSize);
    fun queryObjectPager(field: String, obj: Any, pageSize: Int): IPager<T> {
        return AdhocPager({
            queryPageObjects(field, obj, it - 1, pageSize);
        });
    }

    //Page
    fun getPage(page: Int, length: Int): List<I> {
        if(_sqlPage == null)
            throw IllegalStateException("DB Store [${name}] does not have ordered fields to provide pages");
        val query = _sqlPage!!(page, length) ?: throw IllegalStateException("Paged db not setup for ${_name}");
        return deserializeIndexes(dbDaoBase.getMultiple(query));
    }
    fun getPageObjects(page: Int, length: Int): List<T> = convertObjects(getPage(page, length));

    fun getPager(pageLength: Int = 20): IPager<I> {
        return AdhocPager({
            getPage(it - 1, pageLength);
        });
    }
    fun getObjectPager(pageLength: Int = 20): IPager<T> {
        return AdhocPager({
            getPageObjects(it - 1, pageLength);
        });
    }

    fun delete(item: I) {
        dbDaoBase.delete(item);

        for(index in _indexes)
            index.collection.remove(index.keySelector(item));
    }
    fun delete(id: Long) {
        dbDaoBase.action(_sqlDeleteById(id));

        for(index in _indexes)
            index.collection.values.removeIf { it.id == id }
    }
    fun deleteAll() {
        dbDaoBase.action(_sqlDeleteAll);

        _indexCollection.clear();
        for(index in _indexes)
            index.collection.clear();
    }

    fun convertObject(index: I): T? {
        return index.objOrNull ?: deserializeIndex(index).obj;
    }
    fun convertObjects(indexes: List<I>): List<T> {
        return indexes.mapNotNull { it.objOrNull ?: convertObject(it) };
    }
    fun deserializeIndex(index: I): I {
        if(index.isCorrupted)
            return index;
        if(index.serialized == null) throw IllegalStateException("Cannot deserialize index-only items from [${name}]");
        try {
            val obj = _serializer.deserialize(_class, index.serialized!!);
            index.setInstance(obj);
        }
        catch(ex: Throwable) {
            if(index.serialized != null && index.serialized!!.size > 0) {
                Logger.w("ManagedDBStore", "Corrupted object in ${name} found [${index.id}], deleting due to ${ex.message}", ex);
                index.isCorrupted = true;
                delete(index.id!!);
            }
        }
        index.serialized = null;
        return index;
    }
    fun deserializeIndexes(indexes: List<I>): List<I> {
        for(index in indexes)
            deserializeIndex(index);
        return indexes.filter { !it.isCorrupted }
    }

    fun serialize(obj: T): ByteArray {
        return _serializer.serialize(_class, obj);
    }


    private fun validateFieldName(prop: KProperty<*>): String {
        val declaringClass = prop.javaField?.declaringClass;
        if(declaringClass != descriptor.indexClass().java)
            throw IllegalStateException("Cannot query by property [${prop.name}] from ${declaringClass?.simpleName} not part of ${descriptor.indexClass().simpleName}");
        return prop.name;
    }

    companion object {
        inline fun <reified T, I: ManagedDBIndex<T>, D:  ManagedDBDatabase<T, I, DA>, DA: ManagedDBDAOBase<T, I>> create(name: String, descriptor: ManagedDBDescriptor<T, I, D, DA>, serializer: KSerializer<T>? = null)
            = ManagedDBStore(name, descriptor, kotlin.reflect.typeOf<T>(), JsonStoreSerializer.create(serializer));
    }

    //Pair<(I)->Any, ConcurrentMap<Any, I>>
    class IndexDescriptor<I>(
        val keySelector: (I) -> Any,
        val collection: ConcurrentMap<Any, I>,
        val checkChange: Boolean
    )

    class ColumnMetadata(
        val field: Field,
        val info: ColumnIndex,
        val ordered: ColumnOrdered?
    ) {
        val name get() = if(info.name == ColumnInfo.INHERIT_FIELD_NAME) field.name else info.name;
    }
}