package com.futo.platformplayer.stores

import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.stores.v2.JsonStoreSerializer
import com.futo.platformplayer.stores.v2.ManagedStore
import com.futo.platformplayer.stores.v2.StoreSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable()
class FragmentedStorage {
    companion object {
        val TAG = "LocalStorage";

        @kotlin.jvm.Transient
        val jsonSerializer = Json { ignoreUnknownKeys = true; };

        var _cachedFiles = hashMapOf<String, FragmentedStorageFile>();
        var _cachedDirectories = hashMapOf<String, IFragmentedStorageDirectory>();

        var _filesDir: File? = null;
        val isInitialized: Boolean get() = _filesDir != null;

        fun initialize(filesDir: File) {
            _filesDir = filesDir;
        }
        inline fun <reified T> storeJson(name: String, serializer: KSerializer<T>? = null): ManagedStore<T> = store(name, JsonStoreSerializer.create(serializer), null, null);
        inline fun <reified T> storeJson(parentDir: File, name: String, serializer: KSerializer<T>? = null): ManagedStore<T> = store(name, JsonStoreSerializer.create(serializer), null, parentDir);
        inline fun <reified T> storeJson(name: String, prettyName: String? = null, parentDir: File? = null): ManagedStore<T> = store(name, JsonStoreSerializer.create(), prettyName, parentDir);
        inline fun <reified  T> store(name: String, serializer: StoreSerializer<T>, prettyName: String? = null, parentDir: File? = null): ManagedStore<T> {
            return ManagedStore(name, parentDir ?: _filesDir!!, kotlin.reflect.typeOf<T>() , serializer);
        }

        inline fun <reified T> replace(text: String, verify: Boolean = true): T where T: FragmentedStorageFile {
            if(verify) {
                val dir = getOrCreateDirectory("temp");
                val tempFile = File(dir, UUID.randomUUID().toString());
                tempFile.writeText(text);

                val parsed = if (FragmentedStorageFileJson::class.java.isAssignableFrom(T::class.java)) {
                    loadJsonFile<T>(tempFile, null)
                } else if (FragmentedStorageFileString::class.java.isAssignableFrom(T::class.java)) {
                    loadTextFile<T>(tempFile, null)
                } else {
                    throw NotImplementedError("Unknown file type for ${T::class.java.name}");
                }

                if (parsed == null) {
                    throw IllegalStateException("Failed to import type [${T::class.java.name}]");
                }
            }

            val name = T::class.java.name;
            synchronized(_cachedFiles) {
                val cachedFile = _cachedFiles[name];
                val file = cachedFile?.getUnderlyingFile() ?: File(_filesDir, "${T::class.java.name}.json");
                file.writeText(text);
                _cachedFiles.remove(name);
            }
            return load();
        }

        inline fun <reified T> get(reload: Boolean = false): T where T : FragmentedStorageFile {
            val name = T::class.java.name;

            synchronized(_cachedFiles) {
                if(reload) {
                    _cachedFiles.remove(name);
                }

                var instance = _cachedFiles[name];
                if (instance == null) {
                    instance = load<T>();
                    _cachedFiles[name] = instance;
                    return instance;
                }

                return instance as T;
            }
        }
        inline fun <reified T> get(name: String): T where T : FragmentedStorageFile {
            synchronized(_cachedFiles) {
                var instance = _cachedFiles.get(name);
                if (instance == null) {
                    instance = load<T>(name);
                    _cachedFiles[name] = instance;
                    return instance;
                }

                return instance as T;
            }
        }
        inline fun <reified T : IFragmentedStorageDirectory> getDirectory(): T {
            val name = T::class.java.name;

            synchronized(_cachedDirectories) {
                var instance = _cachedDirectories.get(name);
                if (instance == null) {
                    instance = loadDirectory<T>(getOrCreateDirectory(name));
                    _cachedDirectories[name] = instance;
                    return instance;
                }

                return instance as T;
            }
        }

        inline fun <reified T> load(): T where T : FragmentedStorageFile {
            if (_filesDir == null) {
                throw Exception("Files dir should be initialized before loading a file.")
            }

            val storageFile = File(_filesDir, "${T::class.java.name}.json");
            val storageBakFile = File(_filesDir, "${T::class.java.name}.json.bak");
            return loadFile(storageFile, storageBakFile);
        }
        inline fun <reified T> load(dir: File, fileName: String): T where T : FragmentedStorageFile {
            val storageFile = File(dir, fileName);
            val storageBakFile = File(dir, "${fileName}.bak");
            return loadFile(storageFile, storageBakFile);
        }
        inline fun <reified T> load(fileName: String): T where T : FragmentedStorageFile {
            if (_filesDir == null) {
                throw Exception("Files dir should be initialized before loading a file.")
            }

            val storageFile = File(_filesDir, "${fileName}.json");
            val storageBakFile = File(_filesDir, "${fileName}.json.bak");
            return loadFile(storageFile, storageBakFile);
        }

        fun loadFile(dir: File, fileName: String): File {
            return File(dir, fileName);
        }

        fun deleteFile(dir: File, fileName: String) {
            val storageFile = File(dir, fileName);
            val storageBakFile = File(dir, "${fileName}.bak");

            if(storageFile.exists()) {
                storageFile.delete();
            }

            if(storageBakFile.exists()) {
                storageBakFile.delete();
            }
        }


        fun getOrCreateDirectory(dirName: String) : File {
            if (_filesDir == null) {
                throw Exception("Files dir should be initialized before loading a file.")
            }

            val dirFile = File(_filesDir, dirName);
            if(!dirFile.exists())
                dirFile.mkdir();
            return dirFile;
        }
        inline fun <reified T> loadFile(file: File, bakFile: File?): T where T : FragmentedStorageFile {
            val typeName = T::class.java.name;
            if (file.exists()) {
                val resp = if(FragmentedStorageFileJson::class.java.isAssignableFrom(T::class.java)) {
                    loadJsonFile<T>(file, bakFile)
                } else if(FragmentedStorageFileString::class.java.isAssignableFrom(T::class.java)) {
                    loadTextFile<T>(file, bakFile)
                } else {
                    throw NotImplementedError("Unknown file type for $typeName");
                }

                if(resp != null) {
                    return resp;
                }
            } else {
                Logger.w(TAG, "Failed to fragment storage because the file does not exist. Attempting backup. [${typeName}]");
            }

            if (bakFile?.exists() == true) {
                val resp = if(FragmentedStorageFileJson::class.java.isAssignableFrom(T::class.java)) {
                    loadJsonFile<T>(file, bakFile, true)
                } else if(FragmentedStorageFileString::class.java.isAssignableFrom(T::class.java)) {
                    loadTextFile<T>(file, bakFile, true)
                } else {
                    throw NotImplementedError("Unknown file type");
                }

                if(resp != null) {
                    return resp;
                }
            } else {
                Logger.w(TAG, "Failed to fragment storage because the backup file does not exist. Using default instance. [${typeName}]");
            }

            return (T::class.java.getDeclaredConstructor().newInstance() as T).withFile(file, bakFile) as T;
        }
        inline fun <reified T> loadDirectory(file: File): T where T : IFragmentedStorageDirectory {
            return (T::class.java.getDeclaredConstructor().newInstance() as T).withDirectory(file) as T;
        }

        inline fun <reified T : FragmentedStorageFile > loadJsonFile(file: File, bakFile: File?, fromBak: Boolean = false) : T? {
            try {
                val json = (if(!fromBak) file else bakFile)?.readText() ?: return null;
                val fileObj = jsonSerializer.decodeFromString<T>(json);
                if(fromBak && bakFile != null) {
                    bakFile.copyTo(file, true);
                }
                return fileObj.withFile(file, bakFile) as T;
            } catch (e: Throwable) {
                if(!fromBak) {
                    Logger.e(TAG, "Failed to load fragment storage. Attempting backup.", e);
                } else {
                    Logger.e(TAG, "Failed to load fragment storage. Using default instance.", e);
                }
            }
            return null;
        }
        inline fun <reified T : FragmentedStorageFile> loadTextFile(file: File, bakFile: File?, fromBak: Boolean = false) : T? {
            try {
                val text = (if(!fromBak) file else bakFile)?.readText() ?: return null;
                val fileObj = (T::class.java.getDeclaredConstructor().newInstance() as T).withFile(file, bakFile) as T
                (fileObj as FragmentedStorageFileString).decode(text);
                if(fromBak && bakFile != null) {
                    bakFile.copyTo(file, true);
                }
                return fileObj.withFile(file, bakFile) as T;
            } catch (e: Throwable) {
                if(!fromBak) {
                    Logger.w(TAG, "Failed to load fragment storage. Attempting backup.", e);
                } else {
                    Logger.w(TAG, "Failed to load fragment storage. Using default instance.", e);
                }
            }
            return null;
        }
    }
    //endregion
}