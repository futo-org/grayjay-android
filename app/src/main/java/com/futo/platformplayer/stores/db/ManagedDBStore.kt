package com.futo.platformplayer.stores.db

import androidx.room.ColumnInfo
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import com.futo.platformplayer.api.media.structures.AdhocPager
import com.futo.platformplayer.api.media.structures.IPager
import com.futo.platformplayer.assume
import com.futo.platformplayer.models.HistoryVideo
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.stores.db.types.DBHistory
import com.futo.platformplayer.stores.v2.JsonStoreSerializer
import com.futo.platformplayer.stores.v2.StoreSerializer
import kotlinx.serialization.KSerializer
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.reflect.KClass
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

    private var _dbDescriptor: ManagedDBDescriptor<T, I, D, DA>;

    private val _columnInfo: List<ColumnMetadata>;

    private val _sqlGet: (Long)-> SimpleSQLiteQuery;
    private val _sqlGetAll: (LongArray)-> SimpleSQLiteQuery;
    private val _sqlAll: SimpleSQLiteQuery;
    private val _sqlCount: SimpleSQLiteQuery;
    private val _sqlDeleteAll: SimpleSQLiteQuery;
    private val _sqlDeleteById: (Long) -> SimpleSQLiteQuery;
    private var _sqlIndexed: SimpleSQLiteQuery? = null;
    private var _sqlPage: ((Int, Int) -> SimpleSQLiteQuery)? = null;

    val className: String? get() = _class.classifier?.assume<KClass<*>>()?.simpleName;

    val name: String;

    private val _indexes: ArrayList<Pair<(I)->Any, ConcurrentMap<Any, I>>> = arrayListOf();
    private val _indexCollection = ConcurrentHashMap<Long, I>();

    private var _withUnique: Pair<(I)->Any, ConcurrentMap<Any, I>>? = null;

    constructor(name: String, descriptor: ManagedDBDescriptor<T, I, D, DA>, clazz: KType, serializer: StoreSerializer<T>, niceName: String? = null) {
        _dbDescriptor = descriptor;
        _name = name;
        this.name = niceName ?: name.let {
            if(it.isNotEmpty())
                return@let it[0].uppercase() + it.substring(1);
            return@let name;
        };
        _serializer = serializer;
        _class = clazz;
        _columnInfo = _dbDescriptor.indexClass().memberProperties
            .filter { it.hasAnnotation<ColumnIndex>() && it.name != "serialized" }
            .map { ColumnMetadata(it.javaField!!, it.findAnnotation<ColumnIndex>()!!, it.findAnnotation<ColumnOrdered>()) };

        val indexColumnNames = _columnInfo.map { it.name };

        val orderedColumns = _columnInfo.filter { it.ordered != null }.sortedBy { it.ordered!!.priority };
        val orderSQL = if(orderedColumns.size > 0)
            " ORDER BY " + orderedColumns.map { "${it.name} ${if(it.ordered!!.descending) "DESC" else "ASC"}" }.joinToString(", ");
        else "";

        _sqlGet = { SimpleSQLiteQuery("SELECT * FROM ${_dbDescriptor.table_name} WHERE id = ?", arrayOf(it)) };
        _sqlGetAll = { SimpleSQLiteQuery("SELECT * FROM ${_dbDescriptor.table_name} WHERE id IN (?)", arrayOf(it)) };
        _sqlAll = SimpleSQLiteQuery("SELECT * FROM ${_dbDescriptor.table_name} ${orderSQL}");
        _sqlCount = SimpleSQLiteQuery("SELECT COUNT(id) FROM ${_dbDescriptor.table_name}");
        _sqlDeleteAll = SimpleSQLiteQuery("DELETE FROM ${_dbDescriptor.table_name}");
        _sqlDeleteById = { id -> SimpleSQLiteQuery("DELETE FROM ${_dbDescriptor.table_name} WHERE id = :id", arrayOf(id)) };
        _sqlIndexed = SimpleSQLiteQuery("SELECT ${indexColumnNames.joinToString(", ")} FROM ${_dbDescriptor.table_name}");

        if(orderedColumns.size > 0) {
            _sqlPage = { page, length ->
                SimpleSQLiteQuery("SELECT * FROM ${_dbDescriptor.table_name} ${orderSQL} LIMIT ? OFFSET ?", arrayOf(length, page * length));
            }
        }
    }

    fun withIndex(keySelector: (I)->Any, indexContainer: ConcurrentMap<Any, I>, withUnique: Boolean = false): ManagedDBStore<I, T, D, DA> {
        if(_sqlIndexed == null)
            throw IllegalStateException("Can only create indexes if sqlIndexOnly is implemented");
        _indexes.add(Pair(keySelector, indexContainer));

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

    fun load(): ManagedDBStore<I, T, D, DA> {
        _db = Room.databaseBuilder(StateApp.instance.context, _dbDescriptor.dbClass().java, _name)
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()
        _dbDaoBase = _db!!.base() as ManagedDBDAOBase<T, I>;
        if(_indexes.any()) {
            val allItems = _dbDaoBase!!.getMultiple(_sqlIndexed!!);
            for(index in _indexes)
                index.second.putAll(allItems.associateBy(index.first));
        }

        return this;
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
        val newIndex = _dbDescriptor.create(obj);
        val unique = getUnique(newIndex);
        if(unique != null)
            return unique.id!!;

        newIndex.serialized = serialize(obj);
        newIndex.id = dbDaoBase.insert(newIndex);
        newIndex.serialized = null;


        if(!_indexes.isEmpty()) {
            for (index in _indexes) {
                val key = index.first(newIndex);
                index.second.put(key, newIndex);
            }
        }
        return newIndex.id!!;
    }
    fun update(id: Long, obj: T) {
        val newIndex = _dbDescriptor.create(obj);
        newIndex.id = id;
        newIndex.serialized = serialize(obj);
        dbDaoBase.update(newIndex);
        newIndex.serialized = null;

        if(!_indexes.isEmpty()) {
            for (index in _indexes) {
                val key = index.first(newIndex);
                index.second.put(key, newIndex);
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

    fun getAllObjects(vararg id: Long): List<T> = getAll(*id).map { it.obj!! };
    fun getAll(vararg id: Long): List<I> {
        return deserializeIndexes(dbDaoBase.getMultiple(_sqlGetAll(id)));
    }

    fun getObjectPage(page: Int, length: Int): List<T> = convertObjects(getPage(page, length));
    fun getObjectPager(pageLength: Int = 20): IPager<T> {
        return AdhocPager({
            getObjectPage(it - 1, pageLength);
        });
    }
    fun getPage(page: Int, length: Int): List<I> {
        if(_sqlPage == null)
            throw IllegalStateException("DB Store [${name}] does not have ordered fields to provide pages");
        val query = _sqlPage!!(page, length) ?: throw IllegalStateException("Paged db not setup for ${_name}");
        return dbDaoBase.getMultiple(query);
    }
    fun getPager(pageLength: Int = 20): IPager<I> {
        return AdhocPager({
            getPage(it - 1, pageLength);
        });
    }

    fun delete(item: I) {
        dbDaoBase.delete(item);

        for(index in _indexes)
            index.second.remove(index.first(item));
    }
    fun delete(id: Long) {
        dbDaoBase.action(_sqlDeleteById(id));
        for(index in _indexes)
            index.second.values.removeIf { it.id == id }
    }
    fun deleteAll() {
        dbDaoBase.action(_sqlDeleteAll);

        _indexCollection.clear();
        for(index in _indexes)
            index.second.clear();
    }

    fun convertObject(index: I): T? {
        return index.obj ?: deserializeIndex(index).obj;
    }
    fun convertObjects(indexes: List<I>): List<T> {
        return indexes.mapNotNull { it.obj ?: convertObject(it) };
    }
    fun deserializeIndex(index: I): I {
        if(index.serialized == null) throw IllegalStateException("Cannot deserialize index-only items from [${name}]");
        val obj = _serializer.deserialize(_class, index.serialized!!);
        index.obj = obj;
        index.serialized = null;
        return index;
    }
    fun deserializeIndexes(indexes: List<I>): List<I> {
        for(index in indexes)
            deserializeIndex(index);
        return indexes;
    }

    fun serialize(obj: T): ByteArray {
        return _serializer.serialize(_class, obj);
    }

    companion object {
        inline fun <reified T, I: ManagedDBIndex<T>, D:  ManagedDBDatabase<T, I, DA>, DA: ManagedDBDAOBase<T, I>> create(name: String, descriptor: ManagedDBDescriptor<T, I, D, DA>, serializer: KSerializer<T>? = null)
            = ManagedDBStore(name, descriptor, kotlin.reflect.typeOf<T>(), JsonStoreSerializer.create(serializer));
    }

    class ColumnMetadata(
        val field: Field,
        val info: ColumnIndex,
        val ordered: ColumnOrdered?
    ) {
        val name get() = if(info.name == ColumnInfo.INHERIT_FIELD_NAME) field.name else info.name;
    }
}