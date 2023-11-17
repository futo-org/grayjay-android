package com.futo.platformplayer.stores.db

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import com.futo.platformplayer.assume
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.stores.v2.JsonStoreSerializer
import com.futo.platformplayer.stores.v2.StoreSerializer
import kotlinx.serialization.KSerializer
import java.util.concurrent.ConcurrentMap
import kotlin.reflect.KClass
import kotlin.reflect.KType

class ManagedDBStore<I: ManagedDBIndex<T>, T, D: ManagedDBDatabase<T, I, DA>, DA: ManagedDBDAOBase<T, I>> {
    private val _class: KType;
    private val _name: String;
    private val _serializer: StoreSerializer<T>;

    private var _db: ManagedDBDatabase<T, I, *>? = null;
    private var _dbDaoBase: ManagedDBDAOBase<T, I>? = null;
    val dbDaoBase: ManagedDBDAOBase<T, I> get() = _dbDaoBase ?: throw IllegalStateException("Not initialized db [${name}]");

    private var _dbDescriptor: ManagedDBDescriptor<T, I, D, DA>;

    private val _sqlAll: SimpleSQLiteQuery;
    private val _sqlDeleteAll: SimpleSQLiteQuery;
    private var _sqlIndexed: SimpleSQLiteQuery? = null;

    val className: String? get() = _class.classifier?.assume<KClass<*>>()?.simpleName;

    val name: String;

    private val _indexes: ArrayList<Pair<(I)->Any, ConcurrentMap<Any, I>>> = arrayListOf();


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

        _sqlAll = SimpleSQLiteQuery("SELECT * FROM $_name" + if(descriptor.ordered.isNullOrEmpty()) "" else " ${descriptor.ordered}");
        _sqlDeleteAll = SimpleSQLiteQuery("DELETE FROM ${_name}");
        _sqlIndexed = descriptor.sqlIndexOnly(_name);
    }

    fun withIndex(keySelector: (I)->Any, indexContainer: ConcurrentMap<Any, I>): ManagedDBStore<I, T, D, DA> {
        if(_sqlIndexed == null)
            throw IllegalStateException("Can only create indexes if sqlIndexOnly is implemented");
        _indexes.add(Pair(keySelector, indexContainer));

        return this;
    }


    fun load(): ManagedDBStore<I, T, D, DA> {
        _db = Room.databaseBuilder(StateApp.instance.context, _dbDescriptor.dbClass(), _name)
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

    fun insert(obj: T) {
        val newIndex = _dbDescriptor.create(obj);
        newIndex.serialized = serialize(obj);
        newIndex.id = dbDaoBase.insert(newIndex);
        newIndex.serialized = null;

        if(!_indexes.isEmpty()) {
            for (index in _indexes) {
                val key = index.first(newIndex);
                index.second.put(key, newIndex);
            }
        }
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
        return dbDaoBase.getMultiple(_sqlAll);
    }

    fun getObject(id: Long) = convertObject(get(id));
    fun get(id: Long): I {
        return dbDaoBase.get(SimpleSQLiteQuery("SELECT * FROM $_name WHERE id = ?", arrayOf(id)));
    }

    fun getAllObjects(vararg id: Long): List<T> = convertObjects(getAll(*id));
    fun getAll(vararg id: Long): List<I> {
        return dbDaoBase.getMultiple(SimpleSQLiteQuery("SELECT * FROM $_name WHERE id IN (?)", arrayOf(id)));
    }

    fun getPageObjects(page: Int, length: Int): List<T> = convertObjects(getPage(page, length));
    fun getPage(page: Int, length: Int): List<I> {
        val query = _dbDescriptor.sqlPage(_name, page, length) ?: throw IllegalStateException("Paged db not setup for ${_name}");
        return dbDaoBase.getMultiple(query);
    }
    fun delete(item: I) {
        dbDaoBase.delete(item);

        for(index in _indexes)
            index.second.remove(index.first(item));
    }
    fun deleteAll() {
        dbDaoBase.action(_sqlDeleteAll);

        for(index in _indexes)
            index.second.clear();
    }


    fun convertObject(index: ManagedDBIndex<T>): T? {
        return index.serialized?.let {
            _serializer.deserialize(_class, it);
        };
    }
    fun convertObjects(indexes: List<ManagedDBIndex<T>>): List<T> {
        return indexes.mapNotNull { convertObject(it) };
    }

    fun serialize(obj: T): ByteArray {
        return _serializer.serialize(_class, obj);
    }

    companion object {
        inline fun <reified T, I: ManagedDBIndex<T>, D:  ManagedDBDatabase<T, I, DA>, DA: ManagedDBDAOBase<T, I>> create(name: String, descriptor: ManagedDBDescriptor<T, I, D, DA>, serializer: KSerializer<T>? = null)
            = ManagedDBStore(name, descriptor, kotlin.reflect.typeOf<T>(), JsonStoreSerializer.create(serializer));
    }
}