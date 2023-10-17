package com.futo.platformplayer.stores.v2

import com.futo.platformplayer.assume
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File
import java.io.FileNotFoundException
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.javaType

class ManagedStore<T>{
    private val _class: KType;
    private val _name: String;
    private val _dir: File;
    private val _files: HashMap<T, ManagedFile> = hashMapOf();
    private val _serializer: StoreSerializer<T>;

    private val _toReconstruct: ArrayList<ManagedFile> = ArrayList();

    private var _isLoaded = false;

    private var _withBackup: Boolean = true;
    private var _reconstructStore: ReconstructStore<T>? = null;

    private var _withUnique: ((T) -> Any)? = null;

    val className: String? get() = _class.classifier?.assume<KClass<*>>()?.simpleName;

    val name: String;

    constructor(name: String, dir: File, clazz: KType, serializer: StoreSerializer<T>, niceName: String? = null) {
        _name = name;
        this.name = niceName ?: name.let {
            if(it.length > 0)
                return@let it[0].uppercase() + it.substring(1);
            return@let name;
        };
        _serializer = serializer;
        _class = clazz;
        _dir = File(dir, name);
        if(!_dir.exists())
            _dir.mkdir();
    }

    fun withUnique(handler: (T) -> Any): ManagedStore<T> {
        _withUnique = handler;
        return this;
    }
    fun withRestore(backup: ReconstructStore<T>): ManagedStore<T> {
        _reconstructStore = backup;
        return this;
    }
    fun withoutBackup(): ManagedStore<T>{
        _withBackup = false;
        return this;
    }

    fun load(): ManagedStore<T> {
        synchronized(_files) {
            _files.clear();
            val newObjs = _dir.listFiles().map { it.nameWithoutExtension }.distinct().toList().map { fileId ->
                //Logger.i(TAG, "FILE:" + it.name);
                val mfile = ManagedFile(fileId, _dir);
                val obj = mfile.load(this, _withBackup);
                if(obj == null) {
                    Logger.w(TAG, "Deleting ${logName(mfile.id)}");
                    mfile.delete(false);
                    if(mfile.reconstructFile.exists()) {
                        _toReconstruct.add(mfile);
                        Logger.i(TAG, "Reconstruction required: ${logName(fileId)}");
                    }
                }

                return@map Pair(obj, mfile);
            }.filter { it.first != null };

            for (obj in newObjs)
                _files.put(obj.first!!, obj.second);
        }
        _isLoaded = true;
        return this;
    }
    fun getMissingReconstructionCount(): Int {
        synchronized(_toReconstruct) {
            return _toReconstruct.size;
        }
    }
    fun hasMissingReconstructions(): Boolean {
        synchronized(_toReconstruct) {
            return _toReconstruct.any();
        }
    }

    fun deleteMissing() {
        synchronized(_toReconstruct) {
            for(file in _toReconstruct)
                file.delete(true);
            _toReconstruct.clear();
        }
    }
    suspend fun importReconstructions(items: List<String>, onProgress: ((Int, Int)->Unit)? = null): ReconstructionResult {
        var successes = 0;
        val exs = ArrayList<Throwable>();

        val total = items.size;
        var finished = 0;

        val builder = ReconstructStore.Builder();

        for (recon in items) {
            onProgress?.invoke(0, total);
            //Retry once
            for (i in 0 .. 1) {
                try {
                    Logger.i(TAG, "Importing ${logName(recon)}");
                    val reconId = createFromReconstruction(recon, builder);
                    successes++;
                    Logger.i(TAG, "Imported ${logName(reconId)}");
                    break;
                } catch (ex: Throwable) {
                    Logger.e(TAG, "Failed to reconstruct import", ex);
                    if (i == 1) {
                        exs.add(ex);
                    }
                }
            }
            finished++;
            onProgress?.invoke(finished, total);
        }
        return ReconstructionResult(successes, exs, builder.messages);
    }
    
    suspend fun reconstructMissing(onProgress: ((Int, Int)->Unit)? = null): ReconstructionResult {
        var successes = 0;
        val exs = ArrayList<Throwable>();
        val missings = synchronized(_toReconstruct) { _toReconstruct.toList(); }

        val total = missings.size;
        var finished = 0;

        val builder = ReconstructStore.Builder();

        for (missing in missings) {
            //Retry once
            for (i in 0 .. 1) {
                try {
                    Logger.i(TAG, "Started reconstructing ${logName(missing.id)}");
                    val reconstructed = missing.reconstruct(this, builder);

                    missing.write(_serializer.serialize(_class, reconstructed), _withBackup);
                    synchronized(_files) {
                        _files.put(reconstructed, missing);
                    }
                    synchronized(_toReconstruct) {
                        _toReconstruct.remove(missing);
                    }
                    successes++;
                    Logger.i(TAG, "Reconstructed ${logName(missing.id)}");
                    break;
                } catch (ex: Throwable) {
                    Logger.e(TAG, "Failed to reconstruct ${logName(missing.id)}", ex);

                    if (i == 1) {
                        exs.add(ex);
                    }
                }
                finished++;
                onProgress?.invoke(finished, total);
            }
        }
        return ReconstructionResult(successes, exs, builder.messages);
    }

    fun getItems(): List<T> {
        synchronized(_files) {
            return _files.map { it.key };
        }
    }
    fun queryItem(query: (Iterable<T>)->T?) : T? {
        synchronized(_files) {
            return query(_files.keys.asIterable());
        }
    }
    fun hasItems(): Boolean {
        synchronized(_files) {
            return _files.any();
        }
    }
    fun hasItem(query: (T)-> Boolean): Boolean {
        synchronized(_files) {
            return _files.keys.any { query(it) };
        }
    }
    fun findItem(query: (T)->Boolean): T? {
        synchronized(_files) {
            return _files.keys.find(query);
        }
    }
    fun findItems(query: (T)->Boolean): List<T> {
        synchronized(_files) {
            return _files.keys.filter(query);
        }
    }

    fun getFile(obj: T): ManagedFile? {
        synchronized(_files) {
            if(_files.containsKey(obj))
                return _files[obj];
            return null;
        }
    }


    fun saveAsync(obj: T, withReconstruction: Boolean = false) {
        val scope = StateApp.instance.scopeOrNull;
        if(scope != null)
            scope.launch(Dispatchers.IO) {
                try {
                    save(obj, withReconstruction);
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to save.", e);
                }
            };
        else
            save(obj, withReconstruction);
    }
    fun saveAllAsync(objs: List<T>, withReconstruction: Boolean = false) {
        val scope = StateApp.instance.scopeOrNull;
        if(scope != null)
            scope.launch(Dispatchers.IO) {
                saveAll(objs, withReconstruction);
            };
        else
            saveAll(objs, withReconstruction);
    }
    fun save(obj: T, withReconstruction: Boolean = false, onlyExisting: Boolean = false) {
        synchronized(_files) {
            val uniqueVal = if(_withUnique != null)
                _withUnique!!(obj);
            else null;

            var file = getFile(obj);
            if (file != null) {
                Logger.v(TAG, "Saving file ${logName(file.id)}");
                val encoded = _serializer.serialize(_class, obj);
                file.write(encoded, _withBackup);
                if(_reconstructStore != null && (_reconstructStore!!.backupOnSave || withReconstruction))
                    saveReconstruction(file, obj);
            }
            else if(!onlyExisting && (uniqueVal == null || !_files.any { _withUnique!!(it.key) == uniqueVal })) {
                file = saveNew(obj);
                if(_reconstructStore != null && (_reconstructStore!!.backupOnCreate || withReconstruction))
                    saveReconstruction(file, obj);
            }
        }
    }
    fun saveAll(items: List<T>, withReconstruction: Boolean = false, onlyExisting: Boolean = false) {
        for(obj in items)
            save(obj, withReconstruction, onlyExisting);
    }

    suspend fun createFromReconstruction(reconstruction: String, builder: ReconstructStore.Builder): String {
        if(_reconstructStore == null)
            throw IllegalStateException("Can't reconstruct as no reconstruction is implemented for this type");

        val id = UUID.randomUUID().toString();
        val reconstruct = _reconstructStore!!.toObjectWithHeader(id, reconstruction, builder);
        save(reconstruct);
        return id;
    }

    fun delete(item: T) {
        synchronized(_files) {
            val file = _files[item];
            if(file != null) {
                if(item is IStoreItem)
                    item.onDelete();
                _files.remove(item);
                Logger.v(TAG, "Deleting file ${logName(file.id)}");
                file.delete();
            }
        }
    }
    fun deleteAll() {
        synchronized(_files) {
            val keys = _files.keys.toList();
            for(key in keys)
                delete(key);
        }
    }

    private fun saveNew(obj: T): ManagedFile {
        synchronized(_files) {
            val id = UUID.randomUUID().toString();
            Logger.v(TAG, "New file ${logName(id)}");
            val encoded = _serializer.serialize(_class, obj);

            val mfile = ManagedFile(id, _dir);
            mfile.write(encoded, _withBackup);

            _files.put(obj, mfile);
            return mfile;
        }
    }

    fun getAllReconstructionStrings(withHeader: Boolean = false): List<String> {
        if(_reconstructStore == null)
            throw IllegalStateException("Can't reconstruct as no reconstruction is implemented for this type");

        return getItems().map {
            getReconstructionString(it, withHeader)
        };
    }
    fun getReconstructionString(obj: T, withHeader: Boolean = false): String {
        if(_reconstructStore == null)
            throw IllegalStateException("Can't reconstruct as no reconstruction is implemented for this type");

        if(withHeader)
            return _reconstructStore!!.toReconstructionWithHeader(obj, className ?: "");
        else
            return _reconstructStore!!.toReconstruction(obj);
    }
    private fun saveReconstruction(file: ManagedFile, obj: T) {
        if(_reconstructStore == null)
            return;
        val reconstruction = getReconstructionString(obj, true);
        file.writeReconstruction(reconstruction);
    }

    fun isReconstructionIdentifier(identifier: String): Boolean {
        if(_reconstructStore == null)
            throw IllegalStateException("Can't reconstruct as no reconstruction is implemented for this type");

        return identifier == (_reconstructStore?.identifierName ?: className)
    }
    fun isReconstructionHeader(recon: String): Boolean {
        val identifier = getReconstructionIdentifier(recon);
        return identifier != null && isReconstructionIdentifier(identifier);
    }

    class ManagedFile(
        val id: String,
        val dir: File
    ) {
        val file: File = File(dir, id);
        val bakFile: File = File(dir, id + ".bak");
        val reconstructFile: File = File(dir, id + ".rec");

        fun <T> load(store: ManagedStore<T>, withBackup: Boolean = true): T? {
            synchronized(this) {
                try {
                    if(!file.exists())
                        throw FileNotFoundException();
                    val data = read();

                    //Uncomment to test migration
                    //if(className == "Subscription") throw IllegalStateException("Test Exception");

                    return store._serializer.deserialize(store._class, data);
                }
                catch(ex: Throwable) {
                    if(ex !is FileNotFoundException)
                        Logger.w(TAG, "Failed to parse ${store.logName(id)}", ex);

                    if(withBackup) {
                        val backData = readBackup();
                        try {
                            if (backData != null) {
                                Logger.i(TAG, "Loading from backup ${store.logName(id)}");
                                return store._serializer.deserialize(store._class, backData);
                            } else Logger.i(TAG, "No backup exists for ${store.logName(id)}")
                        } catch (bakEx: Throwable) {
                            Logger.w(TAG, "Failed to bakfile parse ${store.logName(id)}", bakEx);
                        }
                    }
                }

                Logger.w(TAG, "No object from ${store.logName(id)}");
                return null;
            }
        }

        suspend fun <T> reconstruct(store: ManagedStore<T>, builder: ReconstructStore.Builder): T {
            if(store._reconstructStore == null)
                throw IllegalStateException("No reconstruction logic exists?");

            val reconstruction = readReconstruction()
                ?: throw FileNotFoundException("No reconstruction found");

            val reconstructed: T;
            try {
                reconstructed = store._reconstructStore!!.toObjectWithHeader(id, reconstruction, builder);
            }
            catch(ex: Throwable) {
                throw ex;
            }
            return reconstructed;
        }


        fun write(data: ByteArray, withBackup: Boolean = true) {
            if(withBackup && file.exists())
                file.copyTo(bakFile, true);
            file.writeBytes(data);
        }
        fun writeReconstruction(str: String) {
            reconstructFile.writeText(str, Charsets.UTF_8);
        }

        fun read(): ByteArray {
            return file.readBytes();
        }
        fun readBackup(): ByteArray? {
            if(bakFile.exists())
                return bakFile.readBytes();
            return null;
        }
        fun readReconstruction(): String? {
            if(reconstructFile.exists())
                return reconstructFile.readText(Charsets.UTF_8);
            return null;
        }

        fun delete(deleteReconstruction: Boolean = true) {
            if(file.exists())
                file.delete();
            if(bakFile.exists())
                bakFile.delete();
            if(deleteReconstruction && reconstructFile.exists())
                reconstructFile.delete();
        }
    }

    data class ReconstructionResult(
        val success: Int = 0,
        val exceptions: List<Throwable>,
        val messages: List<String>
    );

    private fun logName(id: String?): String {
        return "${_name}:[${(_class.classifier as KClass<*>).simpleName}] ${id ?: ""}";
    }

    companion object {
        val TAG = "ManagedStore";
        val RECONSTRUCTION_HEADER_OPERATOR = "@/";

        fun getReconstructionIdentifier(recon: String): String? {
            if(!recon.startsWith(RECONSTRUCTION_HEADER_OPERATOR) || !recon.contains("\n"))
                return null;
            else
                return recon.substring(2, recon.indexOf("\n"));
        }
    }
}

interface StoreSerializer<T> {
    fun serialize(clazz: KType, obj: T): ByteArray;
    fun deserialize(clazz: KType, obj: ByteArray): T;
}

class JsonStoreSerializer<T>: StoreSerializer<T> {
    private val _serializer: KSerializer<T>
    val jsonSer = Json { ignoreUnknownKeys = true; encodeDefaults = true; }

    constructor(serializer: KSerializer<T>) {
        _serializer = serializer;
    }

    override fun serialize(clazz: KType, obj: T): ByteArray {
        val json = jsonSer.encodeToString<T>(_serializer,obj)//gson.toJson(obj);
        return json.toByteArray(Charsets.UTF_8);
    }

    override fun deserialize(clazz: KType, obj: ByteArray): T {
        val json = String(obj, Charsets.UTF_8);
        try {
            return jsonSer.decodeFromString<T>(_serializer, json);
        }
        catch(ex: Throwable) {
            Logger.e(ManagedStore.TAG, "Json for ${(clazz.classifier as KClass<*>).simpleName}:\n" + json, ex);
            throw ex;
        }
    }

    companion object {
        inline fun <reified T> create(serializer: KSerializer<T>? = null): JsonStoreSerializer<T> {
            return JsonStoreSerializer<T>(if(serializer != null) serializer else serializer<T>());
        }
    }
}