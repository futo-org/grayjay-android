package com.futo.platformplayer.stores

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File

interface IFragmentedStorageDirectory {
    fun withDirectory(dir: File): IFragmentedStorageDirectory;
}

@Serializable
open class FragmentedStorageDirectory : IFragmentedStorageDirectory {
    private val TAG = "FragmentedStorageDirectory";

    @Transient
    var directory: File? = null;

    fun getFiles() : List<String> {
        return directory?.listFiles()
            ?.filter { it.extension != ".bak" }
            ?.map { it.name } ?: listOf();
    }

    fun hasFile(name: String) : Boolean {
        return File(directory, name).exists();
    }
    fun getFileReference(name: String): File {
        return FragmentedStorage.loadFile(directory!!, name);
    }
    inline fun <reified T : FragmentedStorageFile> getFileOrCreate(name : String) : T{
        return FragmentedStorage.load(directory!!, name);
    }
    fun deleteFile(name: String) {
        FragmentedStorage.deleteFile(directory!!, name);
    }

    override fun withDirectory(dir: File): IFragmentedStorageDirectory {
        directory = dir;
        return this;
    }
}